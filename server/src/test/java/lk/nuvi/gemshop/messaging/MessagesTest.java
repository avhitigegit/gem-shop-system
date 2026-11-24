package lk.nuvi.gemshop.messaging;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P4-04 Messages and birthday wishes. Own database: it changes the settings, sends wishes and thank-you messages. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=messages")
class MessagesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired lk.nuvi.gemshop.common.crypto.FieldCrypto crypto;
    @Autowired PasswordEncoder encoder;
    @Autowired AutoMessages auto;
    @Autowired PlatformTransactionManager txm;
    @MockitoSpyBean MailService mail;

    private final Map<String, Signed> who = new LinkedHashMap<>();

    @BeforeEach
    void signIn() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ? "
                + "where email like '%@ceylongemhouse.lk' or email like '%@fernandoassociates.lk'", encoder.encode(SignIn.PASSWORD));
        for (String e : new String[] {OWNER, SALES, ACCOUNTANT}) {
            who.put(e, SignIn.login(mvc, e));
        }
        doReturn(true).when(mail).send(anyString(), anyString(), anyString());
        doReturn(true).when(mail).send(anyString(), anyString(), anyString(), anyString(), any(byte[].class), anyString());
        jdbc.update("update message_settings set email_on = true, bd_on = true, bd_email = true, auto_thank_you = true,"
                + " auto_invoice_email = true, wa_enabled = false, wa_verified = false");
    }

    @Test
    void sampleSettingsAndRoles() throws Exception {
        JsonNode s = read(get("/api/messages/settings"), OWNER);
        assertThat(s.path("smtp").path("host").asString()).isEqualTo("smtp.gmail.com");
        assertThat(s.path("smtp").path("port").asInt()).isEqualTo(587);
        assertThat(s.path("ses").path("domain").asString()).isEqualTo("ceylongemhouse.lk");
        assertThat(s.path("whatsapp").path("enabled").asBoolean()).isFalse();
        assertThat(s.path("auto").path("thankYou").asBoolean()).isTrue();
        assertThat(s.path("birthday").path("time").asString()).isEqualTo("09:00");
        assertThat(s.path("emailTestMode").asBoolean()).isTrue();
        assertThat(s.path("whatsappTestMode").asBoolean()).isTrue();
        assertThat(s.toString()).doesNotContain("password\"").doesNotContain("\"token\"");

        JsonNode st = read(get("/api/messages/status"), SALES);
        assertThat(st.path("emailOn").asBoolean()).isTrue();
        assertThat(st.path("whatsappReady").asBoolean()).isFalse();
        mvc.perform(get("/api/messages/settings").with(who.get(SALES).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/messages/birthdays").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isForbidden());
    }

    @Test
    void smtpSettingsAndTestEmail() throws Exception {
        Map<String, Object> f = Map.of("host", "smtp.gmail.com", "port", 587, "security", "STARTTLS", "user", "sales@ceylongemhouse.lk",
                "password", "abcd efgh ijkl mnop", "from", "Ceylon Gem House <sales@ceylongemhouse.lk>");
        JsonNode v = read(json(put("/api/messages/smtp"), f, OWNER));
        assertThat(v.path("smtp").path("hasPassword").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select smtp_password from message_settings", String.class)).startsWith("v1:");
        // empty password keeps the saved one
        Map<String, Object> again = new LinkedHashMap<>(f);
        again.put("password", "");
        read(json(put("/api/messages/smtp"), again, OWNER));
        assertThat(jdbc.queryForObject("select smtp_password from message_settings", String.class)).startsWith("v1:");
        mvc.perform(json(put("/api/messages/smtp"), Map.of("host", "x", "port", 0, "security", "TLS", "user", "u", "from", "f"), OWNER))
                .andExpect(status().isBadRequest());

        JsonNode t = read(post("/api/messages/smtp/test"), OWNER);
        assertThat(t.path("to").asString()).isEqualTo(OWNER);
        assertThat(t.path("testMode").asBoolean()).isTrue();
        verify(mail).send(eq(OWNER), eq("Test email from the Gem Shop System"), anyString());

        read(json(put("/api/messages/switches"), Map.of("emailOn", false), OWNER));
        mvc.perform(post("/api/messages/smtp/test").with(who.get(OWNER).bearer())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_OFF"));
        assertThat(read(get("/api/messages/status"), SALES).path("emailOn").asBoolean()).isFalse();
        read(json(put("/api/messages/switches"), Map.of("emailOn", true), OWNER));
    }

    @Test
    void whatsappVerifyAndSwitches() throws Exception {
        JsonNode bad = read(json(put("/api/messages/whatsapp"), Map.of("phone", "+94 77 123 4567", "accountId", "abc", "token",
                "EAAGdemoTOKEN1234567890"), OWNER));
        assertThat(bad.path("verified").asBoolean()).isFalse();
        JsonNode ok = read(json(put("/api/messages/whatsapp"), Map.of("phone", "+94 77 123 4567", "accountId", "102938475610293",
                "token", "EAAGdemoTOKEN1234567890"), OWNER));
        assertThat(ok.path("verified").asBoolean()).isTrue();
        assertThat(ok.path("settings").path("whatsapp").path("hasToken").asBoolean()).isTrue();
        assertThat(read(get("/api/messages/status"), SALES).path("whatsappReady").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select wa_token from message_settings", String.class)).startsWith("v1:");

        JsonNode off = read(json(put("/api/messages/switches"), Map.of("whatsappOn", false, "thankYou", false), OWNER));
        assertThat(off.path("whatsapp").path("verified").asBoolean()).isFalse();
        assertThat(off.path("auto").path("thankYou").asBoolean()).isFalse();
        assertThat(off.path("auto").path("invoiceEmail").asBoolean()).isTrue();   // not sent = not changed
        read(json(put("/api/messages/switches"), Map.of("thankYou", true), OWNER));
    }

    @Test
    void birthdayWishesOncePerYearAndOnlyWithPermission() throws Exception {
        String today = LocalDate.now(COLOMBO).toString().substring(5);
        jdbc.update("update customer set birthday = null where birthday = ?", today);
        jdbc.update("update customer set birthday = ?, consent = true, unsubscribed = false, email = ? where code = 'C-001'",
                today, crypto.encrypt("anna@example.de"));
        jdbc.update("update customer set birthday = ?, consent = false where code = 'C-002'", today);
        jdbc.update("delete from birthday_wish where customer_id in (select id from customer where code in ('C-001', 'C-002'))");

        Map<String, Object> form = Map.of("email", true, "whatsapp", true, "time", "23:59", "subject", "Happy birthday from {shop}!",
                "text", "Happy birthday, {name}! Warm wishes from {shop}.");
        JsonNode saved = read(json(put("/api/messages/birthday"), form, OWNER));
        assertThat(saved.path("birthday").path("time").asString()).isEqualTo("23:59");
        mvc.perform(json(put("/api/messages/birthday"), Map.of("email", true, "whatsapp", false, "time", "25:00", "subject", "s", "text", "t"),
                OWNER)).andExpect(status().isBadRequest());

        JsonNode list = read(get("/api/messages/birthdays"), OWNER);
        assertThat(list.path("previewText").asString()).startsWith("Happy birthday, Anna!").contains("Ceylon Gem House");
        assertThat(wishStatus(list, "C-001")).isEqualTo("WILL_SEND");
        assertThat(wishStatus(list, "C-002")).isEqualTo("NO_PERMISSION");

        // the job waits for 23:59; the button sends now
        JsonNode now = read(post("/api/messages/birthdays/send-now"), OWNER);
        assertThat(now.path("sent").asInt()).isEqualTo(1);
        verify(mail).send(eq("anna@example.de"), eq("Happy birthday from Ceylon Gem House!"), anyString());
        assertThat(read(post("/api/messages/birthdays/send-now"), OWNER).path("sent").asInt()).isZero();   // once a year
        assertThat(wishStatus(read(get("/api/messages/birthdays"), OWNER), "C-001")).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("select w.email || '/' || w.whatsapp from birthday_wish w join customer c on c.id = w.customer_id "
                + "where c.code = 'C-001'", String.class)).isEqualTo("sent/WhatsApp off");
        assertThat(jdbc.queryForObject("select count(*) from activity_log where action = 'Birthday wish sent' and user_id is null "
                + "and detail like 'C-001%'", Integer.class)).isEqualTo(1);
        JsonNode profile = read(get("/api/customers/C-001"), OWNER);
        assertThat(profile.path("messages").findValuesAsString("kind")).contains("BIRTHDAY");

        read(json(put("/api/messages/switches"), Map.of("birthdays", false), OWNER));
        mvc.perform(post("/api/messages/birthdays/send-now").with(who.get(OWNER).bearer())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIRTHDAYS_OFF"));
        read(json(put("/api/messages/switches"), Map.of("birthdays", true), OWNER));
    }

    @Test
    void invoiceAfterTheSaleAndThankYouNextDay() throws Exception {
        String number = jdbc.queryForObject("""
                select s.number from sale s join customer c on c.id = s.customer_id
                where s.status = 'COMPLETED' and c.email is not null order by s.sold_at desc limit 1""", String.class);
        jdbc.update("update sale set sent_email = false where number = ?", number);
        TransactionTemplate tx = new TransactionTemplate(txm);
        tx.executeWithoutResult(st -> auto.sendInvoice(number));
        assertThat(jdbc.queryForObject("select sent_email from sale where number = ?", Boolean.class, number)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from message_log where kind = 'INVOICE' and ref = ? and automatic",
                Integer.class, number)).isEqualTo(1);
        verify(mail, atLeastOnce()).send(anyString(), anyString(), anyString(), eq("Invoice " + number + ".pdf"), any(byte[].class),
                eq("application/pdf"));

        Timestamp at = jdbc.queryForObject("select sold_at from sale where number = ?", Timestamp.class, number);
        LocalDate day = at.toInstant().atZone(COLOMBO).toLocalDate();
        int first = thankYou(tx, day);
        assertThat(first).isPositive();
        int second = thankYou(tx, day);
        assertThat(second).isZero();   // once per sale
        assertThat(jdbc.queryForObject("select count(*) from message_log where kind = 'THANK_YOU' and ref = ?", Integer.class, number))
                .isEqualTo(1);

        JsonNode log = read(get("/api/messages/log"), OWNER);
        assertThat(log.path("rows").findValuesAsString("kind")).contains("THANK_YOU", "INVOICE");

        // switched off: nothing more
        read(json(put("/api/messages/switches"), Map.of("thankYou", false), OWNER));
        jdbc.update("delete from message_log where kind = 'THANK_YOU'");
        int third = thankYou(tx, day);
        assertThat(third).isZero();
        read(json(put("/api/messages/switches"), Map.of("thankYou", true), OWNER));
    }

    // ------------------------------------------------------------------ helpers

    private int thankYou(TransactionTemplate tx, LocalDate day) {
        Integer n = tx.execute(st -> auto.thankYou(day));
        return n == null ? 0 : n;
    }

    private static String wishStatus(JsonNode list, String code) {
        for (JsonNode r : list.path("next30")) {
            if (code.equals(r.path("code").asString())) {
                return r.path("status").asString();
            }
        }
        throw new AssertionError("No " + code + " in the next 30 days");
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
