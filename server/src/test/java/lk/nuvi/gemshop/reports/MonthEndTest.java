package lk.nuvi.gemshop.reports;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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

/** P5-03 Accountant and month end. Own database: it closes and re-opens months and adds an expense. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=monthend")
class MonthEndTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ZoneId SHOP = ZoneId.of("Asia/Colombo");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final Map<String, Signed> who = new LinkedHashMap<>();
    private final YearMonth now = YearMonth.now(SHOP);
    private final YearMonth last = now.minusMonths(1);

    @BeforeEach
    void signIn() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ? "
                + "where email like '%@ceylongemhouse.lk' or email like '%@fernandoassociates.lk'", encoder.encode(SignIn.PASSWORD));
        for (String e : new String[] {OWNER, SALES, STAFF, ACCOUNTANT}) {
            who.put(e, SignIn.login(mvc, e));
        }
    }

    @Test
    void profitAndLossAddsUp() throws Exception {
        JsonNode pl = read(get("/api/reports/profit-loss").param("month", last.toString()), ACCOUNTANT);
        JsonNode m = pl.path("figures");
        assertThat(m.path("month").asString()).isEqualTo(last.toString());
        BigDecimal net = d(m, "sales").subtract(d(m, "returns")).subtract(d(m, "cost")).subtract(d(m, "commissions"))
                .subtract(d(m, "cardFees")).subtract(d(m, "expenses"));
        assertThat(d(m, "netProfit")).isEqualByComparingTo(net);

        String from = last.atDay(1).toString();
        String to = now.atDay(1).toString();
        BigDecimal sales = jdbc.queryForObject("select coalesce(sum(i.agreed), 0) from sale_item i join sale s on s.id = i.sale_id "
                + "where s.status = 'COMPLETED' and (s.sold_at at time zone 'Asia/Colombo')::date >= ?::date "
                + "and (s.sold_at at time zone 'Asia/Colombo')::date < ?::date", BigDecimal.class, from, to);
        BigDecimal exp = jdbc.queryForObject("select coalesce(sum(amount), 0) from expense where status = 'APPROVED' "
                + "and (spent_at at time zone 'Asia/Colombo')::date >= ?::date and (spent_at at time zone 'Asia/Colombo')::date < ?::date",
                BigDecimal.class, from, to);
        BigDecimal fees = jdbc.queryForObject("select coalesce(sum(card_fee), 0) from sale where status = 'COMPLETED' "
                + "and (sold_at at time zone 'Asia/Colombo')::date >= ?::date and (sold_at at time zone 'Asia/Colombo')::date < ?::date",
                BigDecimal.class, from, to);
        assertThat(d(m, "sales")).isEqualByComparingTo(sales);
        assertThat(d(m, "expenses")).isEqualByComparingTo(exp);
        assertThat(d(m, "cardFees")).isEqualByComparingTo(fees);
        BigDecimal types = BigDecimal.ZERO;
        for (JsonNode t : pl.path("expenseTypes")) {
            types = types.add(d(t, "total"));
        }
        assertThat(types).isEqualByComparingTo(exp);
        assertThat(pl.path("months").get(0).path("month").asString()).isEqualTo(now.toString());
        assertThat(pl.path("cardFeePct").decimalValue()).isPositive();

        // the branches add up to the whole shop
        BigDecimal branches = BigDecimal.ZERO;
        for (JsonNode b : pl.path("branches")) {
            branches = branches.add(d(b, "netProfit"));
        }
        assertThat(branches).isEqualByComparingTo(d(m, "netProfit"));

        for (String f : new String[] {"pdf", "xlsx"}) {
            mvc.perform(get("/api/reports/profit-loss/export").param("format", f).with(who.get(OWNER).bearer())).andExpect(status().isOk());
        }
        mvc.perform(get("/api/reports/profit-loss").param("month", "Oct").with(who.get(OWNER).bearer())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/profit-loss").with(who.get(SALES).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/accountant/home").with(who.get(STAFF).bearer())).andExpect(status().isForbidden());

        JsonNode home = read(get("/api/accountant/home"), ACCOUNTANT);
        assertThat(home.path("thisMonth").path("month").asString()).isEqualTo(now.toString());
        assertThat(home.path("toClose").path("month").asString()).isEqualTo(last.toString());
        assertThat(home.path("months").size()).isBetween(1, 6);
    }

    @Test
    void closeLockAndReopen() throws Exception {
        // sample data: 3 and 2 months ago were closed by the accountant, last month is open
        JsonNode months = read(get("/api/months"), OWNER).path("months");
        assertThat(row(months, now.minusMonths(3)).path("closed").asBoolean()).isTrue();
        assertThat(row(months, now.minusMonths(3)).path("closedBy").asString()).isEqualTo("Dilani Fernando");
        assertThat(row(months, now.minusMonths(2)).path("closed").asBoolean()).isTrue();
        assertThat(row(months, last).path("closed").asBoolean()).isFalse();
        assertThat(row(months, now).path("ended").asBoolean()).isFalse();

        mvc.perform(post("/api/months/" + now + "/close").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MONTH_NOT_ENDED"));
        mvc.perform(post("/api/months/" + last + "/close").with(who.get(SALES).bearer())).andExpect(status().isForbidden());
        JsonNode closed = read(post("/api/months/" + last + "/close"), ACCOUNTANT);
        assertThat(closed.path("closed").asBoolean()).isTrue();
        mvc.perform(post("/api/months/" + last + "/close").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MONTH_ALREADY_CLOSED"));

        // nothing dated in a closed month can be added or changed
        LocalDate inLast = last.atDay(10);
        Map<String, Object> exp = new HashMap<>(Map.of("spentOn", inLast.toString(), "branch", "ella", "expenseType", "Electricity",
                "amount", 1200, "paidTo", "CEB", "method", "BANK", "bill", upload()));
        mvc.perform(json(post("/api/expenses"), exp, ACCOUNTANT)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MONTH_CLOSED"));
        String old = jdbc.queryForList("select number from expense where status = 'APPROVED' and method <> 'DRAWER' "
                + "and to_char(spent_at at time zone 'Asia/Colombo', 'YYYY-MM') = ?", String.class, last.toString()).stream().findFirst()
                .orElse(null);
        if (old != null) {
            JsonNode e = read(get("/api/expenses/" + old), ACCOUNTANT);
            assertThat(e.path("canCorrect").asBoolean(true)).isFalse();
        }
        mvc.perform(json(post("/api/commissions/pay"), Map.of("lines", List.of(1), "paidOn", inLast.toString(), "method", "BANK"), OWNER))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTH_CLOSED"));
        mvc.perform(json(post("/api/purchases"), purchase(inLast), OWNER)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MONTH_CLOSED"));
        String sale = jdbc.queryForList("select number from sale where status = 'COMPLETED' "
                + "and to_char(sold_at at time zone 'Asia/Colombo', 'YYYY-MM') = ?", String.class, last.toString()).stream().findFirst()
                .orElse(null);
        if (sale != null) {
            mvc.perform(json(post("/api/sales/" + sale + "/cancel"), Map.of("reason", "Wrong customer"), OWNER))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTH_CLOSED"));
        }
        // this month is still open
        exp.put("spentOn", LocalDate.now(SHOP).toString());
        mvc.perform(json(post("/api/expenses"), exp, ACCOUNTANT)).andExpect(status().isCreated());

        // the accountant asks the owner to re-open; the owner approves
        JsonNode asked = read(json(post("/api/months/" + last + "/reopen"), Map.of("reason", "Missing electricity bill"), ACCOUNTANT));
        assertThat(asked.path("closed").asBoolean()).isTrue();
        String ap = asked.path("reopenWaiting").asString();
        assertThat(ap).startsWith("AP-");
        mvc.perform(json(post("/api/months/" + last + "/reopen"), Map.of("reason", "Again"), ACCOUNTANT)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REOPEN_WAITING"));
        mvc.perform(post("/api/approvals/" + ap + "/approve").contentType(MediaType.APPLICATION_JSON).content("{}")
                .with(who.get(OWNER).bearer())).andExpect(status().isOk());
        JsonNode open = row(read(get("/api/months"), ACCOUNTANT).path("months"), last);
        assertThat(open.path("closed").asBoolean()).isFalse();
        assertThat(open.path("reopenReason").asString()).isEqualTo("Missing electricity bill");
        exp.put("spentOn", inLast.toString());
        exp.put("bill", upload());
        mvc.perform(json(post("/api/expenses"), exp, ACCOUNTANT)).andExpect(status().isCreated());

        // the owner closes and re-opens directly
        read(post("/api/months/" + last + "/close"), OWNER);
        JsonNode reopened = read(json(post("/api/months/" + last + "/reopen"), Map.of("reason", "Late supplier invoice"), OWNER));
        assertThat(reopened.path("closed").asBoolean()).isFalse();
        assertThat(reopened.path("reopenedBy").asString()).isEqualTo("Ruwan Perera");
        mvc.perform(json(post("/api/months/" + last + "/reopen"), Map.of("reason", "x"), OWNER)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MONTH_NOT_CLOSED"));
        assertThat(jdbc.queryForObject("select count(*) from activity_log where action in ('Month closed', 'Month re-opened')", Integer.class))
                .isGreaterThanOrEqualTo(4);
    }

    @Test
    void accountantCannotSellChangePricesOrCancel() throws Exception {
        // selling and changing gem prices: the body is checked before the role, so read the roles off the endpoints
        assertThat(roles(lk.nuvi.gemshop.sales.api.SalesController.class, "complete")).doesNotContain("ACCOUNTANT");
        assertThat(roles(lk.nuvi.gemshop.gems.api.GemController.class, "edit")).doesNotContain("ACCOUNTANT");
        assertThat(roles(lk.nuvi.gemshop.sales.api.SalesController.class, "cancelInvoice")).doesNotContain("ACCOUNTANT");
        mvc.perform(json(post("/api/sales/INV-000001/cancel"), Map.of("reason", "x"), ACCOUNTANT)).andExpect(status().isForbidden());
        // sees every branch
        JsonNode all = read(get("/api/reports/sales").param("from", LocalDate.now(SHOP).minusDays(90).toString()), ACCOUNTANT);
        assertThat(all.path("rows").size()).isPositive();
        assertThat(read(get("/api/expenses"), ACCOUNTANT).path("expenses").findValuesAsString("branch")).hasSizeGreaterThan(1);
    }

    @Test
    void taxPackZip() throws Exception {
        String from = now.minusMonths(2).toString();
        JsonNode pv = read(get("/api/tax-pack/preview").param("from", from).param("to", now.toString()), ACCOUNTANT);
        assertThat(pv.path("invoices").asInt()).isPositive();
        assertThat(pv.path("months").size()).isPositive();

        byte[] zip = mvc.perform(get("/api/tax-pack").param("from", from).param("to", now.toString()).with(who.get(ACCOUNTANT).bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        List<String> names = entries(zip);
        assertThat(names).anyMatch(n -> n.endsWith("01 Sales/Invoices.xlsx")).anyMatch(n -> n.endsWith("01 Sales/Invoices.pdf"))
                .anyMatch(n -> n.endsWith("04 Profit and Loss/Profit and Loss.xlsx")).anyMatch(n -> n.contains("07 Credit notes/"))
                .anyMatch(n -> n.contains("06 Daily closings/")).anyMatch(n -> n.contains("02 Purchases/"));

        byte[] small = mvc.perform(get("/api/tax-pack").param("from", from).param("to", from).param("parts", "EXPENSES,PROFIT_LOSS")
                .param("format", "XLSX").param("photos", "false").with(who.get(OWNER).bearer())).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray();
        List<String> few = entries(small);
        assertThat(few).allMatch(n -> n.endsWith(".xlsx")).hasSize(2);

        mvc.perform(get("/api/tax-pack").param("parts", "VAT").with(who.get(OWNER).bearer())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tax-pack").param("to", now.plusMonths(1).toString()).with(who.get(OWNER).bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tax-pack").with(who.get(SALES).bearer())).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from activity_log where action = 'Tax pack downloaded'", Integer.class)).isPositive();
    }

    // ------------------------------------------------------------------ helpers

    private static String roles(Class<?> controller, String method) {
        return java.util.Arrays.stream(controller.getDeclaredMethods()).filter(m -> m.getName().equals(method))
                .map(m -> m.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value()).findFirst()
                .orElseThrow(() -> new AssertionError("No " + method + " on " + controller.getSimpleName()));
    }

    private static List<String> entries(byte[] zip) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                names.add(e.getName());
            }
        }
        return names;
    }

    private static Map<String, Object> purchase(LocalDate day) {
        Map<String, Object> line = new HashMap<>();
        line.putAll(Map.of("name", "Test sapphire", "productType", "Loose gem", "gemType", "Blue Sapphire", "carat", 1.2, "colour", "Blue",
                "shape", "Oval", "treatment", "Heated", "certType", "NONE", "cost", 1000, "price", 2000));
        line.put("min", 1500);
        return Map.of("date", day.toString(), "supplier", "SUP-01", "branch", "ella", "billPhoto", "00000000-0000-0000-0000-000000000001",
                "lines", List.of(line));
    }

    private static JsonNode row(JsonNode months, YearMonth m) {
        for (JsonNode r : months) {
            if (r.path("month").asString().equals(m.toString())) {
                return r;
            }
        }
        throw new AssertionError("No month " + m + " in " + months);
    }

    private static BigDecimal d(JsonNode n, String f) {
        return n.path(f).decimalValue();
    }

    private String upload() throws Exception {
        BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", jpg);
        var req = multipart("/api/files").file(new MockMultipartFile("file", "bill.jpg", "image/jpeg", jpg.toByteArray()))
                .param("kind", "BILL_PHOTO").with(who.get(ACCOUNTANT).bearer());
        return JSON.readTree(mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id")
                .asString();
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
