package lk.nuvi.gemshop.dailycash;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.nuvi.gemshop.SignIn;
import lk.nuvi.gemshop.SignIn.Signed;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import lk.nuvi.gemshop.common.mail.MailService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P3-06 Daily cash. Own database: it closes today's day at Ella, which locks the drawer. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=dailycash")
class DailyCashTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean MailService mail;

    private final Map<String, Signed> who = new LinkedHashMap<>();

    @BeforeEach
    void signIn() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ? "
                + "where email like '%@ceylongemhouse.lk' or email like '%@fernandoassociates.lk'", encoder.encode(SignIn.PASSWORD));
        for (String e : new String[] {OWNER, SALES, STAFF, ACCOUNTANT}) {
            who.put(e, SignIn.login(mvc, e));
        }
        // no mail server in CI: pretend the e-mail went out
        doReturn(true).when(mail).send(anyString(), anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void sampleOpeningsAndClosingsAreLoaded() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from day_closing", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from cash_opening", Integer.class)).isEqualTo(11);
        String shortDay = jdbc.queryForObject("select number from day_closing where note like 'Short by LKR 2,500%'", String.class);
        JsonNode c = read(get("/api/cash/closings/" + shortDay), ACCOUNTANT);
        assertThat(c.path("difference").decimalValue()).isEqualByComparingTo("-2500");
        assertThat(c.path("sentToOwner").asBoolean()).isTrue();
        JsonNode sum = read(get("/api/cash/summary"), OWNER);
        assertThat(sum.path("branch").asString()).isEqualTo("ella");
        assertThat(sum.path("daysNotMatching").asInt()).isGreaterThanOrEqualTo(1);
        byte[] pdf = mvc.perform(get("/api/cash/closings/" + shortDay + "/pdf").with(who.get(OWNER).bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        // shop staff do not handle the drawer; the accountant only looks
        mvc.perform(get("/api/cash/day").with(who.get(STAFF).bearer())).andExpect(status().isForbidden());
        mvc.perform(post("/api/cash/close").with(who.get(ACCOUNTANT).bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lkr\":0,\"usd\":0,\"eur\":0,\"card\":0}")).andExpect(status().isForbidden());
    }

    @Test
    void theDayFromOpeningToClosing() throws Exception {
        // start the day (the sample already entered it – correcting it is allowed until closed)
        JsonNode day = read(json(post("/api/cash/opening"), Map.of("lkr", 30000, "usd", 100, "eur", 50), SALES));
        assertThat(day.path("opening").path("cash").path("lkr").decimalValue()).isEqualByComparingTo("30000");
        assertThat(day.path("lastCounted").isObject()).isTrue();
        JsonNode before = day.path("expected");

        // a cash sale: USD 200, the rest in LKR, with LKR 500 change
        String gem = jdbc.queryForObject("""
                select g.code from gem g join branch b on b.id = g.branch_id
                where b.code = 'ella' and g.status = 'IN_STOCK' and g.price between 100000 and 600000 order by g.code limit 1""", String.class);
        BigDecimal price = jdbc.queryForObject("select price from gem where code = ?", BigDecimal.class, gem);
        BigDecimal usdRate = read(get("/api/cash/day"), SALES).path("rateUsd").decimalValue();
        BigDecimal lkrPart = price.subtract(usdRate.multiply(new BigDecimal("200"))).add(new BigDecimal("500"));
        Map<String, Object> sale = new LinkedHashMap<>();
        sale.put("items", List.of(Map.of("gem", gem, "agreed", price)));
        sale.put("customer", Map.of("name", "Cash Test", "country", "Germany"));
        sale.put("payments", List.of(Map.of("method", "CASH", "currency", "USD", "amount", 200),
                Map.of("method", "CASH", "currency", "LKR", "amount", lkrPart)));
        created(json(post("/api/sales"), sale, SALES));

        // a small drawer expense, and a large one that waits for the owner
        String bill = upload();
        JsonNode tea = created(json(post("/api/cash/expenses"),
                Map.of("expenseType", "Other", "amount", 1200, "paidTo", "Corner shop", "bill", bill), SALES));
        assertThat(tea.path("status").asString()).isEqualTo("APPROVED");
        BigDecimal limit = read(get("/api/cash/day"), SALES).path("largeExpenseLimit").decimalValue();
        JsonNode big = created(json(post("/api/cash/expenses"),
                Map.of("expenseType", "Repairs", "amount", limit.add(BigDecimal.ONE), "paidTo", "Electrician", "bill", upload()), SALES));
        assertThat(big.path("status").asString()).isEqualTo("PENDING");
        assertThat(big.path("approvalNo").asString()).startsWith("AP-");
        // no bill photo → refused (rule 11)
        mvc.perform(json(post("/api/cash/expenses"), Map.of("expenseType", "Transport", "amount", 800, "paidTo", "Tuk-tuk"), SALES))
                .andExpect(status().isBadRequest());

        JsonNode after = read(get("/api/cash/day"), SALES);
        JsonNode e = after.path("expected");
        assertThat(e.path("expected").path("usd").decimalValue())
                .isEqualByComparingTo(before.path("expected").path("usd").decimalValue().add(new BigDecimal("200")));
        BigDecimal out = new BigDecimal("1200").add(limit).add(BigDecimal.ONE);
        assertThat(e.path("expected").path("lkr").decimalValue()).isEqualByComparingTo(before.path("expected").path("lkr").decimalValue()
                .add(lkrPart).subtract(new BigDecimal("500")).subtract(out));
        assertThat(after.path("expenses").size()).isEqualTo(2);
        assertThat(after.path("payments").findValuesAsString("paid")).anyMatch(p -> p.contains("USD 200"));

        // a refund credit note paid back from the drawer
        JsonNode toPay = after.path("refundsToPay");
        BigDecimal refunds = BigDecimal.ZERO;
        if (!toPay.isEmpty()) {
            refunds = toPay.get(0).path("amount").decimalValue();
            JsonNode r = read(post("/api/cash/refunds/" + toPay.get(0).path("creditNote").asString()), SALES);
            assertThat(r.path("expected").path("refundsOut").decimalValue()).isEqualByComparingTo(refunds);
        }

        // close: LKR 1,000 short without a note → asked for one
        JsonNode x = read(get("/api/cash/day"), SALES).path("expected");
        Map<String, Object> close = new LinkedHashMap<>();
        close.put("lkr", x.path("expected").path("lkr").decimalValue().subtract(new BigDecimal("1000")));
        close.put("usd", x.path("expected").path("usd").decimalValue());
        close.put("eur", x.path("expected").path("eur").decimalValue());
        close.put("card", x.path("card").decimalValue());
        JsonNode err = JSON.readTree(mvc.perform(json(post("/api/cash/close"), close, SALES)).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString());
        assertThat(err.path("code").asString()).isEqualTo(DailyCash.NOTE_NEEDED);
        close.put("note", "Short LKR 1,000 – checking");
        JsonNode closed = read(json(post("/api/cash/close"), close, SALES));
        assertThat(closed.path("difference").decimalValue()).isEqualByComparingTo("-1000");
        assertThat(closed.path("number").asString()).startsWith("DC-");
        assertThat(closed.path("sentToOwner").asBoolean()).isTrue();
        verify(mail, atLeastOnce()).send(eq(OWNER), anyString(), anyString(), anyString(), any(), eq("application/pdf"));
        assertThat(jdbc.queryForObject("select count(*) from alert where type = 'CASH_DIFFERENCE' and text like '%DIFFERENCE%'", Integer.class))
                .isPositive();
        assertThat(read(get("/api/cash/day"), OWNER).path("closing").path("number").asString()).isEqualTo(closed.path("number").asString());

        // the day is locked: no new opening, no drawer expense, no second closing
        assertThat(code(json(post("/api/cash/opening"), Map.of("lkr", 1, "usd", 0, "eur", 0), SALES))).isEqualTo(DayLock.DAY_CLOSED);
        assertThat(code(json(post("/api/cash/expenses"),
                Map.of("expenseType", "Other", "amount", 100, "paidTo", "Corner shop", "bill", upload()), SALES))).isEqualTo(DayLock.DAY_CLOSED);
        assertThat(code(json(post("/api/cash/close"), close, SALES))).isEqualTo(DayLock.DAY_CLOSED);
    }

    // ------------------------------------------------------------------ helpers

    private String upload() throws Exception {
        BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", jpg);
        var req = multipart("/api/files").file(new MockMultipartFile("file", "bill.jpg", "image/jpeg", jpg.toByteArray()))
                .param("kind", "BILL_PHOTO").with(who.get(SALES).bearer());
        return JSON.readTree(mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asString();
    }

    private String code(MockHttpServletRequestBuilder req) throws Exception {
        return JSON.readTree(mvc.perform(req).andExpect(status().isConflict()).andReturn().getResponse().getContentAsString()).path("code").asString();
    }

    private JsonNode read(MockHttpServletRequestBuilder req, String as) throws Exception {
        return read(req.with(who.get(as).bearer()));
    }

    private JsonNode read(MockHttpServletRequestBuilder req) throws Exception {
        return JSON.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode created(MockHttpServletRequestBuilder req) throws Exception {
        return JSON.readTree(mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body, String as) throws Exception {
        req.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        return req.with(who.get(as).bearer());
    }
}
