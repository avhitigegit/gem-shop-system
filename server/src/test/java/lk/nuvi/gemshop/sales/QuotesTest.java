package lk.nuvi.gemshop.sales;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P3-03 Keep a gem / give a quote. Own database: it reserves and sells gems. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=quotes")
class QuotesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ZoneId SHOP = ZoneId.of("Asia/Colombo");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired Quotes quotes;
    @MockitoSpyBean MailService mail;

    private final Map<String, Signed> who = new LinkedHashMap<>();

    @BeforeEach
    void signIn() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ? "
                + "where email like '%@ceylongemhouse.lk' or email like '%@fernandoassociates.lk'", encoder.encode(SignIn.PASSWORD));
        for (String e : new String[] {OWNER, SALES, STAFF, ACCOUNTANT}) {
            who.put(e, SignIn.login(mvc, e));
        }
    }

    @Test
    void sampleReservationsAndQuotesAreLoaded() throws Exception {
        JsonNode rs = read(get("/api/quotes").param("kind", "RESERVATION"), SALES);
        assertThat(rs.path("items").size()).isEqualTo(2);
        assertThat(rs.path("reservations").asLong()).isEqualTo(2);
        assertThat(rs.path("quotations").asLong()).isEqualTo(1);
        JsonNode rs1 = read(get("/api/quotes/RS-0001"), SALES);
        assertThat(rs1.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(rs1.path("deposit").decimalValue()).isEqualByComparingTo("30000");
        assertThat(rs1.path("items").get(0).path("code").asString()).isEqualTo("GEM-00118");
        assertThat(read(get("/api/quotes/QT-0002"), SALES).path("status").asString()).isEqualTo("EXPIRED");

        JsonNode scan = read(get("/api/sales/gems/GEM-00118"), SALES);
        assertThat(scan.path("sellable").asBoolean()).isFalse();
        assertThat(scan.path("note").asString()).contains("reserved for").contains("RS-0001");

        mvc.perform(get("/api/quotes").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/quotes").with(who.get(STAFF).bearer())).andExpect(status().isForbidden());
    }

    @Test
    void reserveExtendReleaseAndRules() throws Exception {
        Stock g = inStock(1);
        String no = created(json(post("/api/quotes"), quote("RESERVATION", List.of(line(g.code, g.price)), 3, 10_000), SALES))
                .path("number").asString();
        assertThat(no).startsWith("RS-");
        assertThat(gemStatus(g.code)).isEqualTo("RESERVED");
        assertThat(lastLog()).isEqualTo("Reservation created");

        // the gem cannot go on another bill or reservation
        mvc.perform(json(post("/api/quotes"), quote("QUOTATION", List.of(line(g.code, g.price)), 5, 0), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_SELLABLE"));

        // rules
        Stock h = inStock(2);
        mvc.perform(json(post("/api/quotes"), quote("RESERVATION", List.of(line(h.code, h.min.subtract(BigDecimal.ONE))), 3, 0), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BELOW_MIN"));
        mvc.perform(json(post("/api/quotes"), quote("RESERVATION", List.of(line(h.code, h.price)), -1, 0), SALES))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("expires"));
        mvc.perform(json(post("/api/quotes"), quote("RESERVATION", List.of(line(h.code, h.price)), 3, h.price.doubleValue() + 1), SALES))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("deposit"));
        mvc.perform(json(post("/api/quotes"), quote("RESERVATION", List.of(line(h.code, h.price)), 3, 0), ACCOUNTANT))
                .andExpect(status().isForbidden());
        assertThat(gemStatus(h.code)).isEqualTo("IN_STOCK");

        // extend, release
        String to = LocalDate.now(SHOP).plusDays(10).toString();
        assertThat(read(json(post("/api/quotes/" + no + "/extend"), Map.of("expires", to), SALES)).path("expires").asString())
                .isEqualTo(to);
        assertThat(read(json(post("/api/quotes/" + no + "/release"), Map.of(), SALES)).path("status").asString()).isEqualTo("RELEASED");
        assertThat(gemStatus(g.code)).isEqualTo("IN_STOCK");
        mvc.perform(json(post("/api/quotes/" + no + "/release"), Map.of(), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUOTE_NOT_ACTIVE"));
    }

    @Test
    void quotationPdfSendAndSell() throws Exception {
        doReturn(true).when(mail).send(anyString(), anyString(), anyString(), anyString(), any(byte[].class), anyString());
        Stock g = inStock(3);
        JsonNode q = created(json(post("/api/quotes"), quote("QUOTATION", List.of(line(g.code, g.price)), 14, 0), SALES));
        String no = q.path("number").asString();
        assertThat(no).startsWith("QT-");
        assertThat(gemStatus(g.code)).isEqualTo("IN_STOCK");
        assertThat(q.path("daysLeft").asLong()).isEqualTo(14);

        byte[] pdf = mvc.perform(get("/api/quotes/" + no + "/pdf").with(who.get(SALES).bearer())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        JsonNode sent = read(json(post("/api/quotes/" + no + "/send"), Map.of("email", true, "emailTo", "q@example.com", "whatsapp", false), SALES));
        assertThat(sent.path("email").asBoolean()).isTrue();
        verify(mail).send(eq("q@example.com"), anyString(), anyString(), eq("Quotation " + no + ".pdf"), any(byte[].class),
                eq("application/pdf"));
        assertThat(read(get("/api/quotes/" + no), SALES).path("sentEmail").asBoolean()).isTrue();

        // sell from the quotation
        String code = q.path("customer").path("code").asString();
        JsonNode sale = created(json(post("/api/sales"), Map.of("items", List.of(line(g.code, g.price)), "customer", Map.of("code", code),
                "quote", no, "payments", List.of(pay(g.price))), SALES));
        String inv = sale.path("number").asString();
        JsonNode done = read(get("/api/quotes/" + no), SALES);
        assertThat(done.path("status").asString()).isEqualTo("CONVERTED");
        assertThat(done.path("saleNo").asString()).isEqualTo(inv);
        assertThat(jdbc.queryForObject("select quote_no from sale where number = ?", String.class, inv)).isEqualTo(no);
        mvc.perform(json(post("/api/quotes/" + no + "/send"), Map.of("email", true, "whatsapp", false), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUOTE_NOT_ACTIVE"));
    }

    @Test
    void sellPartOfAReservationAndNightlyExpiry() throws Exception {
        Stock a = inStock(4);
        Stock b = inStock(5);
        JsonNode r = created(json(post("/api/quotes"), quote("RESERVATION", List.of(line(a.code, a.price), line(b.code, b.price)), 3, 0),
                SALES));
        String no = r.path("number").asString();
        String code = r.path("customer").path("code").asString();

        // a normal bill cannot take a reserved gem; the bill for this reservation can
        mvc.perform(json(post("/api/sales/check"), Map.of("items", List.of(line(a.code, a.price))), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_SELLABLE"));
        assertThat(read(json(post("/api/sales/check"), Map.of("items", List.of(line(a.code, a.price)), "quote", no), SALES))
                .path("total").decimalValue()).isEqualByComparingTo(a.price);

        created(json(post("/api/sales"), Map.of("items", List.of(line(a.code, a.price)), "customer", Map.of("code", code), "quote", no,
                "payments", List.of(pay(a.price))), SALES));
        assertThat(gemStatus(a.code)).isEqualTo("SOLD");
        assertThat(gemStatus(b.code)).isEqualTo("IN_STOCK");
        assertThat(read(get("/api/quotes/" + no), SALES).path("status").asString()).isEqualTo("CONVERTED");

        // nightly job: past the date → expired, gem back in stock
        Stock c = inStock(6);
        String old = created(json(post("/api/quotes"), quote("RESERVATION", List.of(line(c.code, c.price)), 0, 0), OWNER))
                .path("number").asString();
        jdbc.update("update quote set expires = ? where number = ?", LocalDate.now(SHOP).minusDays(1), old);
        assertThat(quotes.expireDue()).isGreaterThanOrEqualTo(1);
        assertThat(read(get("/api/quotes/" + old), OWNER).path("status").asString()).isEqualTo("EXPIRED");
        assertThat(gemStatus(c.code)).isEqualTo("IN_STOCK");
        mvc.perform(json(post("/api/sales"), Map.of("items", List.of(line(c.code, c.price)), "customer", Map.of("code", code),
                "quote", old, "payments", List.of(pay(c.price))), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUOTE_NOT_ACTIVE"));
    }

    // ------------------------------------------------------------------ helpers

    private record Stock(String code, BigDecimal price, BigDecimal min) {}

    private Stock inStock(int n) {
        return jdbc.queryForObject("""
                select g.code, g.price, g.min_price from gem g join branch b on b.id = g.branch_id
                where b.code = 'ella' and g.status = 'IN_STOCK' and g.min_price < g.price and g.price < 2000000
                order by g.code desc offset ? limit 1""", (rs, i) -> new Stock(rs.getString(1), rs.getBigDecimal(2), rs.getBigDecimal(3)),
                n * 3);
    }

    private static Map<String, Object> quote(String kind, List<Map<String, Object>> items, int days, double deposit) {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", kind);
        m.put("items", items);
        m.put("customer", Map.of("name", "Quote Test", "country", "Italy"));
        m.put("expires", LocalDate.now(SHOP).plusDays(days).toString());
        m.put("deposit", deposit);
        return m;
    }

    private static Map<String, Object> line(String gem, BigDecimal agreed) {
        return Map.of("gem", gem, "agreed", agreed);
    }

    private static Map<String, Object> pay(BigDecimal amount) {
        return Map.of("method", "CASH", "currency", "LKR", "amount", amount);
    }

    private String gemStatus(String code) {
        return jdbc.queryForObject("select status from gem where code = ?", String.class, code);
    }

    private JsonNode created(MockHttpServletRequestBuilder req) throws Exception {
        return JSON.readTree(mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode read(MockHttpServletRequestBuilder req, String as) throws Exception {
        return JSON.readTree(mvc.perform(req.with(who.get(as).bearer())).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    /** A request already signed in with {@link #json}. */
    private JsonNode read(MockHttpServletRequestBuilder req) throws Exception {
        return JSON.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body, String as) throws Exception {
        req.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        return req.with(who.get(as).bearer());
    }

    private String lastLog() {
        return jdbc.queryForObject("select action from activity_log order by id desc limit 1", String.class);
    }
}
