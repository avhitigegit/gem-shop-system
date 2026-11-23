package lk.nuvi.gemshop.notifications;

import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lk.nuvi.gemshop.SignIn;
import lk.nuvi.gemshop.SignIn.Signed;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
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

/** P4-02 push alerts against a fake push service on localhost. Own database: it adds phones, settings and sales. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"app.test.own-database=push", "app.push.allow-http=true", "app.push.allowed-hosts=localhost"})
class PushTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final Map<String, Signed> who = new LinkedHashMap<>();
    private record Received(String auth, String encoding, byte[] body) {}
    private final BlockingQueue<Received> inbox = new LinkedBlockingQueue<>();
    private final AtomicInteger answer = new AtomicInteger(201);
    private HttpServer service;
    private KeyPair phone;
    private byte[] phoneAuth;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ? "
                + "where email like '%@ceylongemhouse.lk' or email like '%@fernandoassociates.lk'", encoder.encode(SignIn.PASSWORD));
        for (String e : new String[] {OWNER, SALES}) {
            who.put(e, SignIn.login(mvc, e));
        }
        jdbc.update("delete from push_device");
        jdbc.update("delete from push_setting");
        service = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        service.createContext("/push/", ex -> {
            inbox.add(new Received(ex.getRequestHeaders().getFirst("Authorization"), ex.getRequestHeaders().getFirst("Content-Encoding"),
                    ex.getRequestBody().readAllBytes()));
            ex.sendResponseHeaders(answer.get(), -1);
            ex.close();
        });
        service.start();
        phone = WebPushCrypto.newKeyPair();
        phoneAuth = new byte[16];
        new SecureRandom().nextBytes(phoneAuth);
    }

    @AfterEach
    void stop() {
        service.stop(0);
    }

    @Test
    void aSalePushesToTheOwnersPhone() throws Exception {
        String key = read(get("/api/push/key"), OWNER).path("publicKey").asString();
        assertThat(WebPushCrypto.unb64(key)).hasSize(65);
        assertThat(read(get("/api/push/key"), OWNER).path("publicKey").asString()).isEqualTo(key);   // kept, not made again
        assertThat(jdbc.queryForObject("select private_key from push_key", String.class)).doesNotContain("MIG"); // encrypted

        String endpoint = signUp();
        JsonNode settings = read(get("/api/push/settings"), OWNER);
        assertThat(settings.path("devices").size()).isEqualTo(1);
        assertThat(settings.path("kinds").findValuesAsString("kind")).containsExactly("SALE", "APPROVAL", "CASH_DIFFERENCE", "DAY_CLOSED");
        assertThat(settings.path("kinds").findValues("on")).allMatch(JsonNode::asBoolean);

        String invoice = sell(0);
        Received r = inbox.poll(10, TimeUnit.SECONDS);
        assertThat(r).as("push received").isNotNull();
        assertThat(r.encoding()).isEqualTo("aes128gcm");
        assertThat(r.auth()).startsWith("vapid t=").endsWith("k=" + key);
        JsonNode msg = JSON.readTree(new String(WebPushCrypto.decrypt(r.body(), phone.getPrivate(),
                WebPushCrypto.raw((ECPublicKey) phone.getPublic()), phoneAuth), StandardCharsets.UTF_8));
        assertThat(msg.path("title").asString()).isEqualTo("New sale");
        assertThat(msg.path("body").asString()).contains(invoice);
        assertThat(msg.path("url").asString()).contains(invoice);
        waitFor(() -> jdbc.queryForObject("select last_sent_at is not null from push_device", Boolean.class));

        // sales turned off → nothing for the next sale
        JsonNode off = read(json(put("/api/push/settings"), Map.of("kinds", Map.of("SALE", false)), OWNER));
        assertThat(off.path("kinds").get(0).path("on").asBoolean()).isFalse();
        sell(1);
        assertThat(inbox.poll(2, TimeUnit.SECONDS)).isNull();

        // test push still goes
        assertThat(read(post("/api/push/test"), OWNER).path("sent").asInt()).isEqualTo(1);
        assertThat(inbox.poll(10, TimeUnit.SECONDS)).isNotNull();

        // the push service no longer knows the phone → removed
        answer.set(410);
        read(post("/api/push/test"), OWNER);
        assertThat(inbox.poll(10, TimeUnit.SECONDS)).isNotNull();
        waitFor(() -> jdbc.queryForObject("select count(*) = 0 from push_device", Boolean.class));

        // sign up again, then sign out of push
        answer.set(201);
        String again = signUp();
        assertThat(again).isEqualTo(endpoint);
        mvc.perform(delete("/api/push/devices").param("endpoint", again).with(who.get(OWNER).bearer())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from push_device", Integer.class)).isZero();
    }

    @Test
    void onlyTheOwnerAndOnlyRealPushServices() throws Exception {
        mvc.perform(get("/api/push/key").with(who.get(SALES).bearer())).andExpect(status().isForbidden());
        Map<String, Object> bad = device("https://evil.example.com/push/1");
        JsonNode err = JSON.readTree(mvc.perform(json(post("/api/push/devices"), bad, OWNER)).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString());
        assertThat(err.path("code").asString()).isEqualTo("PUSH_ENDPOINT");
    }

    // ------------------------------------------------------------------ helpers

    private String signUp() throws Exception {
        String endpoint = "http://localhost:" + service.getAddress().getPort() + "/push/phone-1";
        mvc.perform(json(post("/api/push/devices"), device(endpoint), OWNER)).andExpect(status().isCreated());
        return endpoint;
    }

    private Map<String, Object> device(String endpoint) {
        return Map.of("endpoint", endpoint, "p256dh", WebPushCrypto.b64(WebPushCrypto.raw((ECPublicKey) phone.getPublic())),
                "auth", WebPushCrypto.b64(phoneAuth), "device", "Test phone");
    }

    private String sell(int n) throws Exception {
        record Stock(String code, BigDecimal price) {}
        Stock g = jdbc.queryForObject("""
                select g.code, g.price from gem g join branch b on b.id = g.branch_id
                where b.code = 'ella' and g.status = 'IN_STOCK' and g.price < 600000
                order by g.code offset ? limit 1""", (rs, i) -> new Stock(rs.getString(1), rs.getBigDecimal(2)), n);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(Map.of("gem", g.code(), "agreed", g.price())));
        body.put("customer", Map.of("name", "Push Test " + n, "country", "France"));
        body.put("payments", List.of(Map.of("method", "CASH", "currency", "LKR", "amount", g.price())));
        String res = mvc.perform(json(post("/api/sales"), body, SALES)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return JSON.readTree(res).path("number").asString();
    }

    private static void waitFor(java.util.concurrent.Callable<Boolean> ok) throws Exception {
        for (int i = 0; i < 50; i++) {
            if (Boolean.TRUE.equals(ok.call())) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Condition not met in time");
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
