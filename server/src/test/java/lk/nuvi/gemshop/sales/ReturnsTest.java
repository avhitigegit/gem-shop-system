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

/** P3-04 Returns. Own database: it sells, returns and exchanges gems. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=returns")
class ReturnsTest {

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
    }

    @Test
    void sampleReturnsAndCreditNoteAreLoaded() throws Exception {
        JsonNode list = read(get("/api/returns"), SALES);
        assertThat(list.findValuesAsString("number")).contains("RT-0001", "RT-0002");
        JsonNode rt2 = read(get("/api/returns/RT-0002"), SALES);
        assertThat(rt2.path("status").asString()).isEqualTo("PENDING");
        assertThat(rt2.path("approvalNo").asString()).isEqualTo("AP-0011");
        JsonNode rt1 = read(get("/api/returns/RT-0001"), SALES);
        assertThat(rt1.path("creditNote").asString()).isEqualTo("CN-0001");
        assertThat(rt1.path("ownerNote").asString()).isEqualTo("OK, refund by card");

        JsonNode cn = read(get("/api/credit-notes/CN-0001"), ACCOUNTANT);
        assertThat(cn.path("amount").decimalValue()).isEqualByComparingTo("165000");
        assertThat(cn.path("guideComm").decimalValue()).isEqualByComparingTo("16500");
        assertThat(cn.path("gem").path("code").asString()).isEqualTo("GEM-00121");

        // the sale for step 2: GEM-00121 already returned, GEM-00086 (INV-000134) waiting
        JsonNode s = read(get("/api/returns/sales/INV-000110"), SALES);
        assertThat(s.path("items").findValues("returned").stream().anyMatch(JsonNode::asBoolean)).isTrue();
        assertThat(read(get("/api/returns/sales/INV-000134"), SALES).path("items").get(0).path("waiting").asString()).isEqualTo("RT-0002");
        assertThat(read(get("/api/returns/sales").param("q", "GEM-00086"), SALES).get(0).path("number").asString()).isEqualTo("INV-000134");

        mvc.perform(get("/api/returns").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/credit-notes").with(who.get(STAFF).bearer())).andExpect(status().isForbidden());
        byte[] pdf = mvc.perform(get("/api/credit-notes/CN-0001/pdf").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        mvc.perform(get("/api/credit-notes/export").param("format", "xlsx").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isOk());
    }

    @Test
    void refundApprovedPutsTheGemBackAndTakesBackCommission() throws Exception {
        doReturn(true).when(mail).send(anyString(), anyString(), anyString(), anyString(), any(byte[].class), anyString());
        Sold sold = sell(1, "G-01");

        // no reason → refused; staff and accountant cannot ask
        mvc.perform(json(post("/api/returns"), Map.of("sale", sold.invoice, "gem", sold.gem, "kind", "REFUND", "reason", ""), SALES))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/returns"), form(sold, "REFUND"), ACCOUNTANT)).andExpect(status().isForbidden());

        JsonNode r = created(json(post("/api/returns"), form(sold, "REFUND"), SALES));
        String no = r.path("number").asString();
        String ap = r.path("approvalNo").asString();
        assertThat(no).startsWith("RT-");
        assertThat(r.path("status").asString()).isEqualTo("PENDING");
        assertThat(gemStatus(sold.gem)).isEqualTo("SOLD");
        // one waiting return per gem
        mvc.perform(json(post("/api/returns"), form(sold, "EXCHANGE"), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETURN_WAITING"));

        // owner approves in Approvals
        mvc.perform(json(post("/api/approvals/" + ap + "/approve"), Map.of("note", "OK"), OWNER)).andExpect(status().isOk());
        JsonNode done = read(get("/api/returns/" + no), SALES);
        assertThat(done.path("status").asString()).isEqualTo("APPROVED");
        String cnNo = done.path("creditNote").asString();
        assertThat(cnNo).startsWith("CN-");
        assertThat(gemStatus(sold.gem)).isEqualTo("RETURNED");
        assertThat(jdbc.queryForObject("select sale_no from gem where code = ?", String.class, sold.gem)).isNull();
        assertThat(jdbc.queryForObject("select released from sale_item i join gem g on g.id = i.gem_id where g.code = ?", Boolean.class,
                sold.gem)).isTrue();

        JsonNode cn = read(get("/api/credit-notes/" + cnNo), SALES);
        assertThat(cn.path("amount").decimalValue()).isEqualByComparingTo(sold.price);
        // guide 10 % of the gem; the only gem on the sale → all staff commission back
        assertThat(cn.path("guideComm").decimalValue()).isEqualByComparingTo(sold.price.movePointLeft(1));
        assertThat(cn.path("staffComm").decimalValue()).isEqualByComparingTo(sold.staffComm);

        // returned gem can be sold again; returning it again is refused
        assertThat(read(get("/api/sales/gems/" + sold.gem), SALES).path("sellable").asBoolean()).isTrue();
        mvc.perform(json(post("/api/returns"), form(sold, "REFUND"), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_RETURNABLE"));

        // a refund credit note cannot pay for a sale
        Stock other = inStock(2);
        mvc.perform(json(post("/api/sales"), sale(other, "C-001", cnNo, List.of(pay(other.price))), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREDIT_NOTE_NOT_USABLE"));

        // send
        JsonNode sent = read(json(post("/api/credit-notes/" + cnNo + "/send"), Map.of("email", true, "emailTo", "r@example.com",
                "whatsapp", false), SALES));
        assertThat(sent.path("email").asBoolean()).isTrue();
        verify(mail).send(eq("r@example.com"), anyString(), anyString(), eq("Credit note " + cnNo + ".pdf"), any(byte[].class),
                eq("application/pdf"));
    }

    @Test
    void rejectedReturnKeepsTheGemSold() throws Exception {
        Sold sold = sell(3, null);
        JsonNode r = created(json(post("/api/returns"), form(sold, "REFUND"), SALES));
        mvc.perform(json(post("/api/approvals/" + r.path("approvalNo").asString() + "/reject"), Map.of("note", "Outside return time"),
                OWNER)).andExpect(status().isOk());
        JsonNode done = read(get("/api/returns/" + r.path("number").asString()), SALES);
        assertThat(done.path("status").asString()).isEqualTo("REJECTED");
        assertThat(done.path("ownerNote").asString()).isEqualTo("Outside return time");
        assertThat(done.path("creditNote").isNull() || done.path("creditNote").isMissingNode()).isTrue();
        assertThat(gemStatus(sold.gem)).isEqualTo("SOLD");
    }

    @Test
    void exchangeCreditNotePaysForTheNewGem() throws Exception {
        Sold sold = sell(4, null);
        JsonNode r = created(json(post("/api/returns"), form(sold, "EXCHANGE"), SALES));
        mvc.perform(json(post("/api/approvals/" + r.path("approvalNo").asString() + "/approve"), Map.of(), OWNER))
                .andExpect(status().isOk());
        String cnNo = read(get("/api/returns/" + r.path("number").asString()), SALES).path("creditNote").asString();

        // a dearer gem: the credit note pays part, cash the rest
        Stock dear = jdbc.queryForObject("""
                select g.code, g.price from gem g join branch b on b.id = g.branch_id
                where b.code = 'ella' and g.status = 'IN_STOCK' and g.price > ? and g.price < ? order by g.code limit 1""",
                (rs, i) -> new Stock(rs.getString(1), rs.getBigDecimal(2)), sold.price, sold.price.multiply(BigDecimal.valueOf(5)));
        BigDecimal rest = dear.price.subtract(sold.price);
        // not enough without the cash
        mvc.perform(json(post("/api/sales"), sale(dear, sold.customer, cnNo, List.of()), SALES))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_ENOUGH"));
        JsonNode inv = created(json(post("/api/sales"), sale(dear, sold.customer, cnNo, List.of(pay(rest))), SALES));
        assertThat(inv.path("payments").get(0).path("method").asString()).isEqualTo("CREDIT");
        assertThat(inv.path("paid").decimalValue()).isEqualByComparingTo(dear.price);
        JsonNode cn = read(get("/api/credit-notes/" + cnNo), SALES);
        assertThat(cn.path("usedSaleNo").asString()).isEqualTo(inv.path("number").asString());
        assertThat(cn.path("usedAmount").decimalValue()).isEqualByComparingTo(sold.price);
        assertThat(read(get("/api/returns/" + r.path("number").asString()), SALES).path("usedSaleNo").asString())
                .isEqualTo(inv.path("number").asString());

        // used once only
        Stock another = inStock(5);
        mvc.perform(json(post("/api/sales"), sale(another, sold.customer, cnNo, List.of(pay(another.price))), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREDIT_NOTE_NOT_USABLE"));
    }

    // ------------------------------------------------------------------ helpers

    private record Stock(String code, BigDecimal price) {}

    private record Sold(String invoice, String gem, BigDecimal price, String customer, BigDecimal staffComm) {}

    private Stock inStock(int n) {
        return jdbc.queryForObject("""
                select g.code, g.price from gem g join branch b on b.id = g.branch_id
                where b.code = 'ella' and g.status = 'IN_STOCK' and g.price < 600000
                order by g.code desc offset ? limit 1""", (rs, i) -> new Stock(rs.getString(1), rs.getBigDecimal(2)), n * 3);
    }

    /** Sells one in-stock gem for its price, cash LKR, to a new customer. */
    private Sold sell(int n, String guide) throws Exception {
        Stock g = inStock(n);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(Map.of("gem", g.code, "agreed", g.price)));
        body.put("customer", Map.of("name", "Return Test " + n, "country", "Germany"));
        if (guide != null) {
            body.put("guide", guide);
        }
        body.put("payments", List.of(pay(g.price)));
        JsonNode s = created(json(post("/api/sales"), body, SALES));
        return new Sold(s.path("number").asString(), g.code, g.price, s.path("customer").path("code").asString(),
                s.path("staffComm").decimalValue());
    }

    private static Map<String, Object> form(Sold s, String kind) {
        return Map.of("sale", s.invoice, "gem", s.gem, "kind", kind, "reason", "Ring size does not fit");
    }

    private static Map<String, Object> sale(Stock g, String customer, String creditNote, List<Map<String, Object>> payments) {
        return Map.of("items", List.of(Map.of("gem", g.code, "agreed", g.price)), "customer", Map.of("code", customer),
                "creditNote", creditNote, "payments", payments);
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

    private JsonNode read(MockHttpServletRequestBuilder req) throws Exception {
        return JSON.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body, String as) throws Exception {
        req.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        return req.with(who.get(as).bearer());
    }
}
