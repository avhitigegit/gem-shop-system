package lk.nuvi.gemshop.offers;

import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** P4-05 Offers. Own database: it sends offers and unsubscribes a customer. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=offers")
class OffersTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** 1×1 PNG. */
    private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired lk.nuvi.gemshop.common.crypto.FieldCrypto crypto;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean MailService mail;

    private final Map<String, Signed> who = new LinkedHashMap<>();

    @BeforeEach
    void signIn() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ? "
                + "where email like '%@ceylongemhouse.lk'", encoder.encode(SignIn.PASSWORD));
        for (String e : new String[] {OWNER, SALES}) {
            who.put(e, SignIn.login(mvc, e));
        }
        doReturn(true).when(mail).send(any(MailService.Rich.class));
        jdbc.update("update message_settings set email_on = true, ses_enabled = true, wa_enabled = false, wa_verified = false");
    }

    @Test
    void sampleOffersAndRoles() throws Exception {
        JsonNode list = read(get("/api/offers"), OWNER);
        assertThat(list.path("offers").findValuesAsString("code")).contains("OF-0001", "OF-0002", "OF-0003");
        assertThat(list.path("totals").path("offers").asInt()).isGreaterThanOrEqualTo(2);
        JsonNode first = row(list, "OF-0001");
        assertThat(first.path("opened").asInt()).isEqualTo(4);
        assertThat(first.path("audience").asString()).isEqualTo("Germany, Netherlands, United Kingdom");
        assertThat(row(list, "OF-0003").path("status").asString()).isEqualTo("DRAFT");

        JsonNode o = read(get("/api/offers/options"), OWNER);
        assertThat(o.path("emailReady").asBoolean()).isTrue();
        assertThat(o.path("whatsappReady").asBoolean()).isFalse();
        assertThat(o.path("templates").findValuesAsString("key")).contains("new_arrivals", "festive_offer");
        assertThat(o.path("gemTypes").toString()).contains("Blue Sapphire").doesNotContain("\"Other\"");

        mvc.perform(get("/api/offers").with(who.get(SALES).bearer())).andExpect(status().isForbidden());
    }

    @Test
    void emailOfferOnlyWithPermissionThenResults() throws Exception {
        jdbc.update("update customer set country = 'Iceland', consent = true, unsubscribed = false, unsubscribed_at = null,"
                + " email = ? where code = 'C-001'", crypto.encrypt("anna@example.is"));
        jdbc.update("update customer set country = 'Iceland', consent = false, email = ? where code = 'C-002'", crypto.encrypt("no@example.is"));
        Map<String, Object> aud = Map.of("channel", "EMAIL", "countries", List.of("Iceland"));
        JsonNode a = read(json(post("/api/offers/audience"), aud));
        assertThat(a.path("matching").asInt()).isEqualTo(2);
        assertThat(a.path("recipients").asInt()).isEqualTo(1);
        assertThat(a.path("excluded").asInt()).isEqualTo(1);

        Map<String, Object> form = new HashMap<>(Map.of("channel", "EMAIL", "name", "Iceland test", "subject", "Rare gems for {name}",
                "body", "Dear {name},\n\nNew sapphires.", "picture", "Padparadscha", "photo", PNG, "countries", List.of("Iceland")));
        JsonNode sent = read(json(post("/api/offers/send"), form));
        assertThat(sent.path("status").asString()).isEqualTo("SENT");
        assertThat(sent.path("sent").asInt()).isEqualTo(1);
        assertThat(sent.path("delivered").asInt()).isEqualTo(1);
        String code = sent.path("code").asString();
        assertThat(code).startsWith("OF-");
        verify(mail, atLeastOnce()).send(argThat((MailService.Rich r) -> r.to().equals("anna@example.is") && r.bulk()
                && r.unsubscribeUrl().contains("/unsubscribe/") && r.html().contains("open.gif") && r.text().startsWith("Dear Anna,")
                && r.image() != null));
        assertThat(jdbc.queryForObject("select count(*) from message_log where kind = 'OFFER' and ref = ?", Integer.class, code))
                .isEqualTo(1);

        // opened (pixel), clicked (link), unsubscribed (link) – each counted once
        String token = jdbc.queryForObject("select r.token from offer_recipient r join offer o on o.id = r.offer_id where o.code = ?",
                String.class, code);
        mvc.perform(get("/api/public/offers/" + token + "/open.gif")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/gif"));
        mvc.perform(get("/api/public/offers/" + token + "/open.gif")).andExpect(status().isOk());
        mvc.perform(get("/api/public/offers/" + token + "/click")).andExpect(status().isFound());
        mvc.perform(get("/api/public/offers/nonsense/open.gif")).andExpect(status().isOk());
        String unsub = jdbc.queryForObject("select unsub_token from customer where code = 'C-001'", String.class);
        mvc.perform(post("/api/public/unsubscribe/" + unsub)).andExpect(status().isOk());
        JsonNode r = row(read(get("/api/offers"), OWNER), code);
        assertThat(r.path("opened").asInt()).isEqualTo(1);
        assertThat(r.path("clicked").asInt()).isEqualTo(1);
        assertThat(r.path("unsubscribed").asInt()).isEqualTo(1);
        assertThat(read(json(post("/api/offers/audience"), aud)).path("recipients").asInt()).isZero();   // rule 8

        JsonNode profile = read(get("/api/customers/C-001"), OWNER);
        assertThat(profile.path("messages").findValuesAsString("kind")).contains("OFFER");

        mvc.perform(json(post("/api/offers/send"), form)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_RECIPIENTS"));
        jdbc.update("update message_settings set ses_enabled = false");
        mvc.perform(json(post("/api/offers/send"), form)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_EMAIL_OFF"));
    }

    @Test
    void whatsappOfferByGemBought() throws Exception {
        Map<String, Object> form = Map.of("channel", "WHATSAPP", "name", "Moonstone offer", "template", "festive_offer", "body",
                "10% off moonstone jewellery.", "gems", List.of("Moonstone", "Blue Sapphire"));
        mvc.perform(json(post("/api/offers/send"), form)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WHATSAPP_OFF"));
        jdbc.update("update message_settings set wa_enabled = true, wa_verified = true");
        JsonNode a = read(json(post("/api/offers/audience"), Map.of("channel", "WHATSAPP", "gems", List.of("Moonstone", "Blue Sapphire"),
                "template", "festive_offer")));
        int expected = a.path("recipients").asInt();
        if (expected == 0) {
            return; // sample data without such buyers
        }
        assertThat(a.path("costUsd").decimalValue()).isPositive();
        JsonNode sent = read(json(post("/api/offers/send"), form));
        assertThat(sent.path("sent").asInt()).isEqualTo(expected);
        assertThat(sent.path("delivered").asInt()).isEqualTo(expected);   // test mode = accepted
        assertThat(sent.path("audience").asString()).isEqualTo("Moonstone, Blue Sapphire");
        mvc.perform(json(post("/api/offers/send"), Map.of("channel", "WHATSAPP", "name", "x", "template", "nope", "body", "y")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void draftsAndTest() throws Exception {
        Map<String, Object> form = new HashMap<>(Map.of("channel", "EMAIL", "name", "Gem guide", "subject", "Your guide", "body",
                "Dear {name}, ...", "picture", "Blue Sapphire", "photo", PNG));
        JsonNode d = read(json(post("/api/offers/draft"), form));
        String code = d.path("code").asString();
        assertThat(d.path("status").asString()).isEqualTo("DRAFT");
        assertThat(d.path("photo").asString()).startsWith("data:image/png;base64,");
        form.put("code", code);
        form.put("name", "Gem guide 2");
        form.remove("photo");
        JsonNode d2 = read(json(post("/api/offers/draft"), form));
        assertThat(d2.path("name").asString()).isEqualTo("Gem guide 2");
        assertThat(d2.path("photo").asString()).startsWith("data:image/png");   // kept

        form.put("photo", "data:text/html;base64,PGI+");
        mvc.perform(json(post("/api/offers/draft"), form)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OFFER_PHOTO"));
        form.remove("photo");
        form.put("subject", "");
        mvc.perform(json(post("/api/offers/draft"), form)).andExpect(status().isBadRequest());

        JsonNode t = read(json(post("/api/offers/test"), Map.of("channel", "EMAIL", "name", "x", "subject", "s", "body", "b")));
        assertThat(t.path("to").asString()).isEqualTo(OWNER);

        mvc.perform(delete("/api/offers/" + code).with(who.get(OWNER).bearer())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/offers/OF-0001").with(who.get(OWNER).bearer())).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ helpers

    private static JsonNode row(JsonNode list, String code) {
        for (JsonNode r : list.path("offers")) {
            if (code.equals(r.path("code").asString())) {
                return r;
            }
        }
        throw new AssertionError("No offer " + code);
    }

    private JsonNode read(MockHttpServletRequestBuilder req, String as) throws Exception {
        return read(req.with(who.get(as).bearer()));
    }

    private JsonNode read(MockHttpServletRequestBuilder req) throws Exception {
        return JSON.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body) throws Exception {
        req.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        return req.with(who.get(OWNER).bearer());
    }
}
