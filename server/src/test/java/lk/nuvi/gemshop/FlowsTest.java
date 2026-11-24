package lk.nuvi.gemshop;

import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.nuvi.gemshop.SignIn.Signed;
import lk.nuvi.gemshop.messaging.Birthdays;
import lk.nuvi.gemshop.common.mail.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * P6-01: one sale followed through every module – payment, drawer, commission, return with the owner's approval, cash
 * refund, closing the day and the month's Profit &amp; Loss – and the timed birthday job. Each module has its own test; this
 * one checks that the money agrees between them. Own database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=flows")
class FlowsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ZoneId SHOP = ZoneId.of("Asia/Colombo");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired lk.nuvi.gemshop.common.crypto.FieldCrypto crypto;
    @Autowired PasswordEncoder encoder;
    @Autowired Birthdays birthdays;
    @MockitoSpyBean MailService mail;

    private final Map<String, Signed> who = new LinkedHashMap<>();

    @BeforeEach
    void signIn() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ? "
                + "where email like '%@ceylongemhouse.lk' or email like '%@fernandoassociates.lk'", encoder.encode(SignIn.PASSWORD));
        for (String e : new String[] {OWNER, SALES}) {
            who.put(e, SignIn.login(mvc, e));
        }
        doReturn(true).when(mail).send(anyString(), anyString(), anyString());
        doReturn(true).when(mail).send(anyString(), anyString(), anyString(), anyString(), any(byte[].class), anyString());
    }

    @Test
    void oneSaleThroughEveryModule() throws Exception {
        String month = YearMonth.now(SHOP).toString();
        JsonNode plBefore = read(get("/api/reports/profit-loss").param("month", month), OWNER).path("figures");

        // start the day at Ella
        read(json(post("/api/cash/opening"), Map.of("lkr", 1000000, "usd", 0, "eur", 0), SALES));   // enough to pay the refund back
        JsonNode day = read(get("/api/cash/day"), SALES);
        JsonNode before = day.path("expected");
        BigDecimal usdRate = day.path("rateUsd").decimalValue();

        // sale with a guide, paid USD 100 cash + card + the rest in LKR cash
        String gem = jdbc.queryForObject("""
                select g.code from gem g join branch b on b.id = g.branch_id
                where b.code = 'ella' and g.status = 'IN_STOCK' and g.price between 200000 and 600000 and g.min_price < g.price
                order by g.code limit 1""", String.class);
        BigDecimal price = jdbc.queryForObject("select price from gem where code = ?", BigDecimal.class, gem);
        BigDecimal usdInLkr = usdRate.multiply(new BigDecimal("100"));
        BigDecimal card = price.multiply(new BigDecimal("0.4")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lkr = price.subtract(usdInLkr).subtract(card).setScale(2, RoundingMode.HALF_UP);
        JsonNode check = read(json(post("/api/sales/check"), Map.of("items", List.of(line(gem, price)), "guide", "G-01"), SALES), SALES);
        BigDecimal guideComm = check.path("guideComm").decimalValue();
        BigDecimal staffComm = check.path("staffComm").decimalValue();
        assertThat(guideComm).isPositive();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(line(gem, price)));
        body.put("customer", Map.of("name", "Flow Test", "country", "Germany"));
        body.put("guide", "G-01");
        body.put("payments", List.of(pay("CASH", "USD", new BigDecimal("100")), pay("CARD", "LKR", card), pay("CASH", "LKR", lkr)));
        JsonNode sale = created(json(post("/api/sales"), body, SALES));
        String invoice = sale.path("number").asString();
        BigDecimal fee = card.multiply(new BigDecimal("0.03")).setScale(2, RoundingMode.HALF_UP);
        assertThat(sale.path("total").decimalValue()).isEqualByComparingTo(price);
        assertThat(sale.path("cardFee").isNull()).as("card fee is a shop cost – not shown to the salesperson").isTrue();
        assertThat(jdbc.queryForObject("select card_fee from sale where number = ?", BigDecimal.class, invoice)).isEqualByComparingTo(fee);

        // commission: guide and staff lines for exactly what the till showed
        assertThat(commission(invoice, "GUIDE")).isEqualByComparingTo(guideComm);
        assertThat(commission(invoice, "STAFF")).isEqualByComparingTo(staffComm);

        // the drawer: cash only (card goes to the card machine)
        JsonNode after = read(get("/api/cash/day"), SALES).path("expected");
        assertThat(after.path("expected").path("usd").decimalValue())
                .isEqualByComparingTo(before.path("expected").path("usd").decimalValue().add(new BigDecimal("100")));
        assertThat(after.path("expected").path("lkr").decimalValue())
                .isEqualByComparingTo(before.path("expected").path("lkr").decimalValue().add(lkr));
        assertThat(after.path("card").decimalValue()).isEqualByComparingTo(before.path("card").decimalValue().add(card));
        assertThat(after.path("cardFee").decimalValue()).isEqualByComparingTo(before.path("cardFee").decimalValue().add(fee));

        // a refund: the salesperson asks, the owner approves, the credit note takes the commission back
        JsonNode ret = created(json(post("/api/returns"), Map.of("sale", invoice, "gem", gem, "kind", "REFUND",
                "reason", "Customer changed their mind"), SALES));
        String ap = ret.path("approvalNo").asString();
        assertThat(ap).startsWith("AP-");
        assertThat(gemStatus(gem)).isEqualTo("SOLD");
        mvc.perform(json(post("/api/approvals/" + ap + "/approve"), Map.of("note", "OK"), SALES)).andExpect(status().isForbidden());
        read(json(post("/api/approvals/" + ap + "/approve"), Map.of("note", "OK"), OWNER));
        mvc.perform(json(post("/api/approvals/" + ap + "/approve"), Map.of("note", "Again"), OWNER)).andExpect(status().isConflict());
        assertThat(gemStatus(gem)).isEqualTo("RETURNED");
        Map<String, Object> cn = jdbc.queryForMap("""
                select c.number, c.amount, c.guide_comm, c.staff_comm from credit_note c
                join sale_return r on r.id = c.return_id join sale s on s.id = r.sale_id where s.number = ?""", invoice);
        assertThat((BigDecimal) cn.get("amount")).isEqualByComparingTo(price);
        assertThat((BigDecimal) cn.get("guide_comm")).isEqualByComparingTo(guideComm);
        assertThat((BigDecimal) cn.get("staff_comm")).isEqualByComparingTo(staffComm);
        assertThat(commission(invoice, "GUIDE")).isZero();
        assertThat(commission(invoice, "STAFF")).isZero();

        // the refund is paid back from the drawer
        String cnNo = (String) cn.get("number");
        JsonNode paid = read(post("/api/cash/refunds/" + cnNo), SALES).path("expected");
        assertThat(paid.path("expected").path("lkr").decimalValue())
                .isEqualByComparingTo(after.path("expected").path("lkr").decimalValue().subtract(price));

        // close the day with exactly what is expected → balanced
        Map<String, Object> close = new LinkedHashMap<>();
        close.put("lkr", paid.path("expected").path("lkr").decimalValue());
        close.put("usd", paid.path("expected").path("usd").decimalValue());
        close.put("eur", paid.path("expected").path("eur").decimalValue());
        close.put("card", paid.path("card").decimalValue());
        JsonNode closed = read(json(post("/api/cash/close"), close, SALES));
        assertThat(closed.path("difference").decimalValue()).isZero();

        // Profit & Loss of the month: sold and returned → only the card fee is left as a cost
        JsonNode pl = read(get("/api/reports/profit-loss").param("month", month), OWNER).path("figures");
        assertThat(d(pl, "sales").subtract(d(plBefore, "sales"))).isEqualByComparingTo(price);
        assertThat(d(pl, "returns").subtract(d(plBefore, "returns"))).isEqualByComparingTo(price);
        assertThat(d(pl, "cost").subtract(d(plBefore, "cost"))).isZero();
        assertThat(d(pl, "commissions").subtract(d(plBefore, "commissions"))).isZero();
        assertThat(d(pl, "cardFees").subtract(d(plBefore, "cardFees"))).isEqualByComparingTo(fee);
        assertThat(d(pl, "netProfit").subtract(d(plBefore, "netProfit"))).isEqualByComparingTo(fee.negate());

        // every step is in the activity log
        assertThat(jdbc.queryForList("select action from activity_log where detail like ? or detail like ?", String.class,
                "%" + invoice + "%", "%" + cnNo + "%")).contains("Sale completed");
    }

    @Test
    void birthdayJobSendsOnceAfterTheChosenTimeAndOnlyWithPermission() throws Exception {
        String today = LocalDate.now(SHOP).toString().substring(5);
        jdbc.update("update message_settings set email_on = true, bd_on = true, bd_email = true, wa_enabled = false, wa_verified = false");
        jdbc.update("update customer set birthday = null where birthday = ?", today);
        jdbc.update("update customer set birthday = ?, consent = true, unsubscribed = false, email = ? where code = 'C-001'",
                today, crypto.encrypt("flow.a@example.de"));
        jdbc.update("update customer set birthday = ?, consent = true, unsubscribed = true, email = ? where code = 'C-002'",
                today, crypto.encrypt("flow.b@example.de"));
        jdbc.update("update customer set birthday = ?, consent = false, unsubscribed = false, email = ? where code = 'C-003'",
                today, crypto.encrypt("flow.c@example.de"));
        jdbc.update("delete from birthday_wish");

        // before the chosen time the job waits
        read(json(put("/api/messages/birthday"), wish("23:59"), OWNER));
        if (java.time.LocalTime.now(SHOP).isBefore(java.time.LocalTime.of(23, 59))) {
            birthdays.job();
            assertThat(wishes()).isZero();
        }

        // after it, one wish to the customer with permission; running again sends nothing
        read(json(put("/api/messages/birthday"), wish("00:00"), OWNER));
        birthdays.job();
        birthdays.job();
        assertThat(wishes()).isOne();
        verify(mail, times(1)).send(eq("flow.a@example.de"), anyString(), anyString());
        verify(mail, times(0)).send(eq("flow.b@example.de"), anyString(), anyString());
        verify(mail, times(0)).send(eq("flow.c@example.de"), anyString(), anyString());
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> wish(String time) {
        return Map.of("email", true, "whatsapp", false, "time", time, "subject", "Happy birthday from {shop}!",
                "text", "Happy birthday, {name}!");
    }

    private int wishes() {
        return jdbc.queryForObject("select count(*) from birthday_wish", Integer.class);
    }

    private BigDecimal commission(String invoice, String payee) {
        return jdbc.queryForObject("""
                select coalesce(sum(l.amount), 0) from commission_line l join sale s on s.id = l.sale_id
                where s.number = ? and l.payee = ?""", BigDecimal.class, invoice, payee);
    }

    private String gemStatus(String code) {
        return jdbc.queryForObject("select status from gem where code = ?", String.class, code);
    }

    private static Map<String, Object> line(String gem, BigDecimal agreed) {
        return Map.of("gem", gem, "agreed", agreed);
    }

    private static Map<String, Object> pay(String method, String currency, BigDecimal amount) {
        return Map.of("method", method, "currency", currency, "amount", amount);
    }

    private static BigDecimal d(JsonNode n, String f) {
        return n.path(f).decimalValue();
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
