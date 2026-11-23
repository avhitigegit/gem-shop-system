package lk.nuvi.gemshop.expenses;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import lk.nuvi.gemshop.SignIn;
import lk.nuvi.gemshop.SignIn.Signed;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P5-01 Expenses. Own database: it adds expenses, monthly bills and approvals. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=expenses")
class ExpensesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired Expenses expenses;

    private final Map<String, Signed> who = new LinkedHashMap<>();

    @BeforeEach
    void signIn() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ? "
                + "where email like '%@ceylongemhouse.lk' or email like '%@fernandoassociates.lk'", encoder.encode(SignIn.PASSWORD));
        for (String e : new String[] {OWNER, SALES, ACCOUNTANT}) {
            who.put(e, SignIn.login(mvc, e));
        }
    }

    @Test
    void sampleExpensesBillsAndRoles() throws Exception {
        JsonNode list = read(get("/api/expenses"), ACCOUNTANT);
        assertThat(jdbc.queryForObject("select count(*) from expense where number <= 'EXP-0029'", Integer.class)).isEqualTo(29);
        assertThat(list.path("months").size()).isGreaterThanOrEqualTo(3);
        JsonNode first = row(list, "EXP-0001");
        assertThat(first.path("method").asString()).isEqualTo("BANK");
        assertThat(first.path("monthlyBill").asString()).isEqualTo("RC-01");
        assertThat(first.path("hasBill").asBoolean()).isTrue();
        assertThat(list.path("expenses").findValuesAsString("method")).contains("DRAWER", "CARD");
        JsonNode pending = null;
        for (JsonNode r : list.path("expenses")) {
            if ("PENDING".equals(r.path("status").asString())) {
                pending = r;
            }
        }
        assertThat(pending).isNotNull();
        assertThat(pending.path("approvalNo").asString()).startsWith("AP-");

        JsonNode detail = read(get("/api/expenses/EXP-0001"), OWNER);
        assertThat(detail.path("sampleBill").asString()).isEqualTo("bill-exp-1.jpg");

        JsonNode bills = read(get("/api/expenses/monthly-bills"), OWNER);
        assertThat(bills.path("bills").findValuesAsString("code")).contains("RC-01", "RC-02", "RC-03");
        assertThat(jdbc.queryForObject("select sum(amount) from monthly_bill where code in ('RC-01','RC-02','RC-03')", java.math.BigDecimal.class)).isEqualByComparingTo("201500");

        JsonNode lists = read(get("/api/settings/lists"), OWNER);
        assertThat(lists.toString()).contains("expense(s)");

        mvc.perform(get("/api/expenses").with(who.get(SALES).bearer())).andExpect(status().isForbidden());
    }

    @Test
    void addCorrectAndApproval() throws Exception {
        String today = LocalDate.now(COLOMBO).toString();
        Map<String, Object> f = new HashMap<>(Map.of("spentOn", today, "branch", "ella", "expenseType", "Electricity", "amount", 15400,
                "paidTo", "Ceylon Electricity Board", "method", "CARD"));
        // a bill photo is required
        mvc.perform(json(post("/api/expenses"), f, ACCOUNTANT)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BILL_REQUIRED"));
        f.put("bill", upload(ACCOUNTANT));
        JsonNode e = created(json(post("/api/expenses"), f, ACCOUNTANT));
        String no = e.path("expense").path("number").asString();
        assertThat(e.path("expense").path("status").asString()).isEqualTo("APPROVED");
        assertThat(e.path("bill").path("url").asString()).contains("/api/files/");

        // drawer cash is paid on Pay out cash
        Map<String, Object> drawer = new HashMap<>(f);
        drawer.put("method", "DRAWER");
        drawer.put("bill", upload(ACCOUNTANT));
        mvc.perform(json(post("/api/expenses"), drawer, ACCOUNTANT)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USE_PAY_OUT"));
        // not in the future, a listed type
        Map<String, Object> bad = new HashMap<>(f);
        bad.put("spentOn", LocalDate.now(COLOMBO).plusDays(1).toString());
        bad.put("bill", upload(ACCOUNTANT));
        mvc.perform(json(post("/api/expenses"), bad, ACCOUNTANT)).andExpect(status().isBadRequest());
        bad.put("spentOn", today);
        bad.put("expenseType", "Holidays");
        mvc.perform(json(post("/api/expenses"), bad, ACCOUNTANT)).andExpect(status().isBadRequest());

        // above the limit: the accountant's correction waits for the owner; the owner's own expense does not
        Map<String, Object> big = new HashMap<>(f);
        big.put("amount", 75000);
        big.remove("bill");
        JsonNode c = read(json(put("/api/expenses/" + no), big, ACCOUNTANT));
        assertThat(c.path("expense").path("status").asString()).isEqualTo("PENDING");
        assertThat(c.path("expense").path("approvalNo").asString()).startsWith("AP-");
        assertThat(jdbc.queryForObject("select count(*) from activity_log where action = 'Expense corrected' and detail like ?",
                Integer.class, no + "%amount LKR 15,400.00 → LKR 75,000.00%")).isEqualTo(1);
        Map<String, Object> mine = new HashMap<>(big);
        mine.put("bill", upload(OWNER));
        JsonNode owner = created(json(post("/api/expenses"), mine, OWNER));
        assertThat(owner.path("expense").path("status").asString()).isEqualTo("APPROVED");

        // report: approved only, by month / type / method / branch
        JsonNode r = read(get("/api/expenses/report"), ACCOUNTANT);
        assertThat(r.path("types").findValuesAsString("expenseType")).contains("Rent", "Electricity");
        assertThat(r.path("months").size()).isGreaterThanOrEqualTo(3);
        assertThat(r.path("methods").findValuesAsString("method")).contains("BANK", "CARD", "DRAWER");
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        for (JsonNode t : r.path("types")) {
            sum = sum.add(t.path("total").decimalValue());
        }
        assertThat(sum).isEqualByComparingTo(r.path("total").decimalValue());
        assertThat(jdbc.queryForObject("select count(*) from expense where number = ? and status = 'PENDING'", Integer.class, no)).isEqualTo(1);
    }

    @Test
    void monthlyBillsAddedOncePerMonth() throws Exception {
        Map<String, Object> f = new HashMap<>(Map.of("branch", "ella", "expenseType", "Water", "amount", 4000, "paidTo",
                "Water Board", "method", "BANK", "day", 1));
        JsonNode b = created(json(post("/api/expenses/monthly-bills"), f, ACCOUNTANT));
        String code = b.path("code").asString();
        assertThat(code).isEqualTo("RC-04");
        assertThat(b.path("addedThisMonth").asBoolean()).isFalse();

        int added = read(post("/api/expenses/monthly-bills/run"), OWNER).path("added").asInt();
        assertThat(added).isGreaterThanOrEqualTo(1);
        assertThat(read(post("/api/expenses/monthly-bills/run"), OWNER).path("added").asInt()).isZero();   // once a month
        String no = jdbc.queryForObject("""
                select e.number from expense e join monthly_bill m on m.id = e.monthly_bill_id where m.code = ?""", String.class, code);
        JsonNode d = read(get("/api/expenses/" + no), ACCOUNTANT);
        assertThat(d.path("expense").path("hasBill").asBoolean()).isFalse();
        assertThat(d.path("expense").path("by").asString()).isEqualTo("System");
        JsonNode withBill = read(json(post("/api/expenses/" + no + "/bill"), Map.of("bill", upload(ACCOUNTANT)), ACCOUNTANT));
        assertThat(withBill.path("expense").path("hasBill").asBoolean()).isTrue();

        f.put("active", false);
        JsonNode off = read(json(put("/api/expenses/monthly-bills/" + code), f, OWNER));
        assertThat(off.path("active").asBoolean()).isFalse();
        assertThat(off.path("next").isNull() || off.path("next").isMissingNode()).isTrue();
        // the nightly job adds nothing more this month
        expenses.run(LocalDate.now(COLOMBO), false);
        assertThat(jdbc.queryForObject("select count(*) from expense e join monthly_bill m on m.id = e.monthly_bill_id where m.code = ?",
                Integer.class, code)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private static JsonNode row(JsonNode list, String number) {
        for (JsonNode r : list.path("expenses")) {
            if (number.equals(r.path("number").asString())) {
                return r;
            }
        }
        throw new AssertionError("No " + number);
    }

    private String upload(String as) throws Exception {
        BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", jpg);
        var req = multipart("/api/files").file(new MockMultipartFile("file", "bill.jpg", "image/jpeg", jpg.toByteArray()))
                .param("kind", "BILL_PHOTO").with(who.get(as).bearer());
        return JSON.readTree(mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id")
                .asString();
    }

    private JsonNode created(MockHttpServletRequestBuilder req) throws Exception {
        return JSON.readTree(mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode read(MockHttpServletRequestBuilder req, String as) throws Exception {
        return read(req.with(who.get(as).bearer()));
    }

    private JsonNode read(MockHttpServletRequestBuilder req) throws Exception {
        return JSON.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body, String as) throws Exception {
        req.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        return req.with(who.get(as).bearer());
    }
}
