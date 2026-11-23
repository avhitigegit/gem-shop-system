package lk.nuvi.gemshop.customers;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P4-03 Customers. Own database: it adds customers, changes permission and unsubscribes them. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"app.test.own-database=customers", "app.whatsapp.verify-token=gem-hook",
    "app.whatsapp.app-secret=test-app-secret"})
class CustomersTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

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
    void sampleCustomersWithBirthdaysPurchasesAndPermission() throws Exception {
        JsonNode list = read(get("/api/customers"), SALES).path("customers");
        assertThat(list.size()).isGreaterThanOrEqualTo(25);
        JsonNode anna = find(list, "C-001");
        assertThat(anna.path("name").asString()).isEqualTo("Anna Müller");
        assertThat(anna.path("birthday").asString()).isEqualTo("04-17");
        assertThat(anna.path("birthdayIn").asInt()).isBetween(0, 366);
        assertThat(anna.path("canMessage").asBoolean()).isTrue();
        assertThat(list.findValues("birthday").stream().filter(JsonNode::isNull).count()).isPositive(); // some gave none

        JsonNode buyer = null;
        for (JsonNode c : list) {
            if (c.path("purchases").asLong() > 0) {
                buyer = c;
                break;
            }
        }
        assertThat(buyer).isNotNull();
        JsonNode p = read(get("/api/customers/" + buyer.path("code").asString()), OWNER);
        assertThat(p.path("purchases").size()).isPositive();
        assertThat(p.path("purchases").get(0).path("gems").size()).isPositive();
        assertThat(p.path("customer").path("spent").decimalValue()).isPositive();

        // passport: masked for the owner, hidden from the salesperson; stored encrypted
        assertThat(read(get("/api/customers/C-001"), OWNER).path("passport").asString()).isEqualTo("•••• 000");
        JsonNode bySales = read(get("/api/customers/C-001"), SALES);
        assertThat(bySales.path("passport").isNull()).isTrue();
        assertThat(bySales.path("hasPassport").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select passport from customer where code = 'C-001'", String.class)).isNotEqualTo("GE4000000");

        // exports (owner) and roles
        mvc.perform(get("/api/customers/export").param("format", "xlsx").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/customers/export").param("format", "pdf").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/customers/export").param("format", "pdf").with(who.get(SALES).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/customers").with(who.get(STAFF).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/customers").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/customers/C-999").with(who.get(OWNER).bearer())).andExpect(status().isNotFound());
    }

    @Test
    void addEditAndThePermissionSwitch() throws Exception {
        Map<String, Object> bad = new LinkedHashMap<>(Map.of("name", "Marta Rossi", "country", "Italy", "birthday", "02-30"));
        mvc.perform(json(post("/api/customers"), bad, SALES)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("customer.birthday"));

        String today = LocalDate.now(java.time.ZoneId.of("Asia/Colombo")).toString().substring(5);
        Map<String, Object> form = new LinkedHashMap<>(Map.of("name", "Marta Rossi", "country", "Italy", "email", "Marta@Example.it",
                "whatsapp", "+39 333 111 2222", "birthday", today, "passport", "ya 1234567", "consent", true));
        JsonNode c = created(json(post("/api/customers"), form, SALES));
        String code = c.path("code").asString();
        assertThat(code).startsWith("C-");
        assertThat(c.path("email").asString()).isEqualTo("marta@example.it");
        assertThat(c.path("birthdayIn").asInt()).isZero();
        assertThat(c.path("canMessage").asBoolean()).isTrue();
        assertThat(read(get("/api/customers/" + code), OWNER).path("passport").asString()).isEqualTo("•••• 567");

        // edit: empty passport keeps the saved one; birthday can be removed
        Map<String, Object> edit = new LinkedHashMap<>(Map.of("name", "Marta Rossi-Bianchi", "country", "Italy",
                "email", "marta@example.it", "whatsapp", "+39 333 111 2222", "passport", ""));
        JsonNode e = read(json(put("/api/customers/" + code), edit, OWNER));
        assertThat(e.path("name").asString()).isEqualTo("Marta Rossi-Bianchi");
        assertThat(e.path("birthday").isNull()).isTrue();
        assertThat(read(get("/api/customers/" + code), OWNER).path("passport").asString()).isEqualTo("•••• 567");
        assertThat(jdbc.queryForObject("select count(*) from activity_log where action = 'Customer edited' and detail like ?",
                Integer.class, code + "%")).isEqualTo(1);

        // permission off and on again
        JsonNode off = read(json(put("/api/customers/" + code + "/consent"), Map.of("consent", false), SALES));
        assertThat(off.path("canMessage").asBoolean()).isFalse();
        JsonNode on = read(json(put("/api/customers/" + code + "/consent"), Map.of("consent", true), SALES));
        assertThat(on.path("canMessage").asBoolean()).isTrue();
        mvc.perform(json(put("/api/customers/" + code), edit, STAFF)).andExpect(status().isForbidden());
    }

    @Test
    void unsubscribeLinkAndWhatsappStop() throws Exception {
        Map<String, Object> form = new LinkedHashMap<>(Map.of("name", "Jan de Vries", "country", "Netherlands",
                "whatsapp", "+31 6 1234 5678", "consent", true));
        String code = created(json(post("/api/customers"), form, OWNER)).path("code").asString();
        String token = jdbc.queryForObject("select unsub_token from customer where code = ?", String.class, code);
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);

        // the public page: no sign-in, first name only
        mvc.perform(get("/api/public/unsubscribe/" + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jan")).andExpect(jsonPath("$.unsubscribed").value(false));
        mvc.perform(post("/api/public/unsubscribe/" + token)).andExpect(status().isOk()).andExpect(jsonPath("$.unsubscribed").value(true));
        mvc.perform(post("/api/public/unsubscribe/" + token)).andExpect(status().isOk()); // twice is fine
        mvc.perform(get("/api/public/unsubscribe/not-a-real-token-at-all-000000")).andExpect(status().isNotFound());
        JsonNode p = read(get("/api/customers/" + code), OWNER);
        assertThat(p.path("customer").path("unsubscribed").asBoolean()).isTrue();
        assertThat(p.path("customer").path("canMessage").asBoolean()).isFalse();
        assertThat(p.path("unsubVia").asString()).isEqualTo("LINK");
        assertThat(jdbc.queryForObject("select count(*) from activity_log where action = 'Unsubscribed' and user_id is null and detail like ?",
                Integer.class, code + "%")).isEqualTo(1);

        // permission given again at the counter
        read(json(put("/api/customers/" + code + "/consent"), Map.of("consent", true), OWNER));

        // WhatsApp webhook: Meta's check, then a signed STOP
        mvc.perform(get("/api/public/whatsapp/webhook").param("hub.mode", "subscribe").param("hub.verify_token", "wrong")
                .param("hub.challenge", "42")).andExpect(status().isForbidden());
        mvc.perform(get("/api/public/whatsapp/webhook").param("hub.mode", "subscribe").param("hub.verify_token", "gem-hook")
                .param("hub.challenge", "42")).andExpect(status().isOk()).andExpect(content().string("42"));

        String hello = inbound("31612345678", "Hello, is the shop open?");
        String stop = inbound("31612345678", "stop");
        mvc.perform(post("/api/public/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(stop))
                .andExpect(status().isForbidden()); // not signed
        mvc.perform(post("/api/public/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(stop)
                .header("X-Hub-Signature-256", "sha256=" + "00".repeat(32))).andExpect(status().isForbidden());
        webhook(hello);
        assertThat(read(get("/api/customers/" + code), OWNER).path("customer").path("canMessage").asBoolean()).isTrue();
        webhook(stop);
        JsonNode after = read(get("/api/customers/" + code), OWNER);
        assertThat(after.path("customer").path("unsubscribed").asBoolean()).isTrue();
        assertThat(after.path("unsubVia").asString()).isEqualTo("WHATSAPP");
    }

    // ------------------------------------------------------------------ helpers

    private static String inbound(String from, String text) throws Exception {
        Map<String, Object> msg = Map.of("from", from, "id", "wamid.1", "type", "text", "text", Map.of("body", text));
        return JSON.writeValueAsString(Map.of("object", "whatsapp_business_account", "entry",
                java.util.List.of(Map.of("id", "1", "changes", java.util.List.of(Map.of("field", "messages", "value",
                        Map.of("messaging_product", "whatsapp", "messages", java.util.List.of(msg))))))));
    }

    private void webhook(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-app-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        mvc.perform(post("/api/public/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Hub-Signature-256", sig)).andExpect(status().isOk());
    }

    private static JsonNode find(JsonNode array, String code) {
        for (JsonNode n : array) {
            if (code.equals(n.path("code").asString())) {
                return n;
            }
        }
        throw new AssertionError("No customer " + code);
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
