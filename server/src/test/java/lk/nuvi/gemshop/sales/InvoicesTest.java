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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

/** P3-02 Invoices: list, PDF, send, cancel with the owner's approval. Own database: it sells and cancels. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=invoices")
class InvoicesTest {

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
    void listFiltersAndSampleCancelRequest() throws Exception {
        JsonNode all = read(get("/api/sales").param("from", "2020-01-01"), ACCOUNTANT);
        assertThat(all.path("total").asLong()).isGreaterThanOrEqualTo(40);
        assertThat(all.path("amount").decimalValue()).isPositive();

        JsonNode one = read(get("/api/sales").param("from", "2020-01-01").param("q", "inv-000140"), SALES);
        assertThat(one.path("total").asLong()).isEqualTo(1);
        JsonNode row = one.path("items").get(0);
        assertThat(row.path("cancelPending").asString()).isEqualTo("AP-0012");
        assertThat(row.path("payment").asString()).isEqualTo("Card");
        assertThat(row.path("payType").asString()).isEqualTo("card");

        JsonNode card = read(get("/api/sales").param("from", "2020-01-01").param("pay", "card"), OWNER);
        card.path("items").forEach(r -> assertThat(r.path("payType").asString()).isEqualTo("card"));
        JsonNode split = read(get("/api/sales").param("from", "2020-01-01").param("pay", "split"), OWNER);
        split.path("items").forEach(r -> assertThat(r.path("payment").asString()).contains("+"));

        JsonNode inv = read(get("/api/sales/INV-000140"), SALES);
        assertThat(inv.path("cancel").path("number").asString()).isEqualTo("AP-0012");
        assertThat(inv.path("cancel").path("status").asString()).isEqualTo("PENDING");
        assertThat(inv.path("cancel").path("reason").asString()).isNotBlank();
        assertThat(inv.path("items").get(0).path("treatment").asString()).isNotBlank();

        mvc.perform(get("/api/sales").with(who.get(STAFF).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/sales").param("from", "2026-02-01").param("to", "2026-01-01").with(who.get(OWNER).bearer()))
                .andExpect(status().isBadRequest());

        // the prototype's waiting cancellation: the owner approves → cancelled, gem back in stock
        mvc.perform(post("/api/approvals/AP-0012/approve").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        JsonNode done = read(get("/api/sales/INV-000140"), OWNER);
        assertThat(done.path("status").asString()).isEqualTo("CANCELLED");
        assertThat(done.path("cancelledBy").asString()).isNotBlank();
        assertThat(gemStatus("GEM-00094")).isEqualTo("IN_STOCK");
    }

    @Test
    void pdfAndExports() throws Exception {
        byte[] pdf = mvc.perform(get("/api/sales/INV-000101/pdf").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf")).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        mvc.perform(get("/api/sales/INV-000101/pdf").with(who.get(STAFF).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/sales/INV-999999/pdf").with(who.get(OWNER).bearer())).andExpect(status().isNotFound());

        mvc.perform(get("/api/sales/export").param("format", "xlsx").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/sales/export").param("format", "pdf").with(who.get(SALES).bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/sales/export").param("format", "doc").with(who.get(OWNER).bearer())).andExpect(status().isBadRequest());
    }

    @Test
    void sendByEmailAndWhatsapp() throws Exception {
        doReturn(true).when(mail).send(anyString(), anyString(), anyString(), anyString(), any(byte[].class), anyString());
        jdbc.update("update sale set sent_email = false, sent_whatsapp = false where number = 'INV-000102'");
        jdbc.update("update message_settings set email_on = true, wa_enabled = true, wa_verified = true");   // P4-04: WhatsApp on

        JsonNode r = read(json(post("/api/sales/INV-000102/send"),
                Map.of("email", true, "emailTo", "buyer@example.com", "whatsapp", true, "whatsappTo", "+49 151 2345 6789"), SALES));
        assertThat(r.path("email").asBoolean()).isTrue();
        assertThat(r.path("whatsapp").asBoolean()).isTrue();
        assertThat(r.path("whatsappTestMode").asBoolean()).isTrue();
        verify(mail).send(eq("buyer@example.com"), anyString(), anyString(), eq("Invoice INV-000102.pdf"), any(byte[].class),
                eq("application/pdf"));
        JsonNode inv = read(get("/api/sales/INV-000102"), SALES);
        assertThat(inv.path("sentEmail").asBoolean()).isTrue();
        assertThat(inv.path("sentWhatsapp").asBoolean()).isTrue();
        assertThat(lastLog()).isEqualTo("Invoice sent");

        mvc.perform(json(post("/api/sales/INV-000102/send"), Map.of("email", false, "whatsapp", false), SALES))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTHING_CHOSEN"));
        mvc.perform(json(post("/api/sales/INV-000102/send"), Map.of("email", true, "emailTo", "not-an-email", "whatsapp", false), SALES))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("emailTo"));
        mvc.perform(json(post("/api/sales/INV-000102/send"), Map.of("email", false, "whatsapp", true, "whatsappTo", "abc"), SALES))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("whatsappTo"));
        mvc.perform(json(post("/api/sales/INV-000102/send"), Map.of("email", true, "whatsapp", false), ACCOUNTANT))
                .andExpect(status().isForbidden());

        // switched off on Email & WhatsApp settings → 409
        jdbc.update("update message_settings set wa_enabled = false, wa_verified = false");
        mvc.perform(json(post("/api/sales/INV-000102/send"), Map.of("email", false, "whatsapp", true, "whatsappTo", "+49 151 2345 6789"),
                SALES)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WHATSAPP_OFF"));
        jdbc.update("update message_settings set email_on = false");
        mvc.perform(json(post("/api/sales/INV-000102/send"), Map.of("email", true, "emailTo", "a@b.lk", "whatsapp", false), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_OFF"));
        jdbc.update("update message_settings set email_on = true");

        // the email server fails → 409, nothing marked
        doReturn(false).when(mail).send(anyString(), anyString(), anyString(), anyString(), any(byte[].class), anyString());
        jdbc.update("update sale set sent_email = false where number = 'INV-000103'");
        mvc.perform(json(post("/api/sales/INV-000103/send"), Map.of("email", true, "emailTo", "a@b.lk", "whatsapp", false), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_SENT"));
        assertThat(read(get("/api/sales/INV-000103"), SALES).path("sentEmail").asBoolean()).isFalse();
    }

    @Test
    void cancelOnlyWithOwnerApprovalAndNeverDeleted() throws Exception {
        String gem = jdbc.queryForObject("""
                select g.code from gem g join branch b on b.id = g.branch_id
                where b.code = 'ella' and g.status = 'IN_STOCK' and g.min_price < g.price order by g.code desc limit 1""", String.class);
        BigDecimal price = jdbc.queryForObject("select price from gem where code = ?", BigDecimal.class, gem);
        Map<String, Object> cust = new HashMap<>();
        cust.put("name", "Cancel Test");
        cust.put("country", "Japan");
        JsonNode sale = JSON.readTree(mvc.perform(json(post("/api/sales"), Map.of("items", List.of(Map.of("gem", gem, "agreed", price)),
                "customer", cust, "payments", List.of(Map.of("method", "CASH", "currency", "LKR", "amount", price))), SALES))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String no = sale.path("number").asString();
        assertThat(gemStatus(gem)).isEqualTo("SOLD");

        mvc.perform(json(post("/api/sales/" + no + "/cancel"), Map.of("reason", " "), SALES)).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/sales/" + no + "/cancel"), Map.of("reason", "x"), ACCOUNTANT)).andExpect(status().isForbidden());

        JsonNode asked = read(json(post("/api/sales/" + no + "/cancel"), Map.of("reason", "Wrong customer selected"), SALES));
        String ap = asked.path("cancel").path("number").asString();
        assertThat(asked.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(asked.path("cancel").path("status").asString()).isEqualTo("PENDING");
        assertThat(gemStatus(gem)).isEqualTo("SOLD");
        mvc.perform(json(post("/api/sales/" + no + "/cancel"), Map.of("reason", "Again"), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCEL_WAITING"));

        // rejected: nothing changes; may ask again
        mvc.perform(json(post("/api/approvals/" + ap + "/reject"), Map.of("note", "Keep it"), OWNER)).andExpect(status().isOk());
        JsonNode kept = read(get("/api/sales/" + no), SALES);
        assertThat(kept.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(kept.path("cancel").path("status").asString()).isEqualTo("REJECTED");
        assertThat(kept.path("cancel").path("note").asString()).isEqualTo("Keep it");

        String ap2 = read(json(post("/api/sales/" + no + "/cancel"), Map.of("reason", "Customer changed mind"), SALES))
                .path("cancel").path("number").asString();
        mvc.perform(json(post("/api/approvals/" + ap2 + "/approve"), Map.of(), OWNER)).andExpect(status().isOk());
        JsonNode gone = read(get("/api/sales/" + no), SALES);
        assertThat(gone.path("status").asString()).isEqualTo("CANCELLED");
        assertThat(gone.path("cancelReason").asString()).isEqualTo("Customer changed mind");
        assertThat(gone.path("items").get(0).path("released").asBoolean()).isTrue();
        assertThat(gemStatus(gem)).isEqualTo("IN_STOCK");
        assertThat(read(get("/api/sales/gems/" + gem), SALES).path("sellable").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from sale where number = ?", Integer.class, no)).isEqualTo(1);

        mvc.perform(json(post("/api/sales/" + no + "/cancel"), Map.of("reason", "Again"), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVOICE_CANCELLED"));
        mvc.perform(json(post("/api/sales/" + no + "/send"), Map.of("email", true, "emailTo", "a@b.lk", "whatsapp", false), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVOICE_CANCELLED"));
        JsonNode list = read(get("/api/sales").param("q", no).param("status", "CANCELLED"), OWNER);
        assertThat(list.path("total").asLong()).isEqualTo(1);
        assertThat(list.path("amount").decimalValue()).isZero();
        // the PDF still opens (with the CANCELLED watermark)
        mvc.perform(get("/api/sales/" + no + "/pdf").with(who.get(OWNER).bearer())).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ helpers

    private String gemStatus(String code) {
        return jdbc.queryForObject("select status from gem where code = ?", String.class, code);
    }

    private JsonNode read(MockHttpServletRequestBuilder req, String as) throws Exception {
        return JSON.readTree(mvc.perform(req.with(who.get(as).bearer())).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    /** A request that is already signed in (built with {@link #json}). */
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
