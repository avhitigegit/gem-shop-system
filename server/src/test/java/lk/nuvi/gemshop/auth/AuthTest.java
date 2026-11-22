package lk.nuvi.gemshop.auth;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.PASSWORD;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.nuvi.gemshop.SignIn;
import lk.nuvi.gemshop.SignIn.Signed;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import lk.nuvi.gemshop.auth.api.AuthController;
import lk.nuvi.gemshop.common.mail.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/** P1-02: sign in, lock, renew, sign out, idle, change and reset password (real PostgreSQL, sample data). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean MailService mail;
    @Autowired lk.nuvi.gemshop.system.sampledata.SampleDataLoader sampleData;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void freshLogins() {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ?", encoder.encode(PASSWORD));
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from password_reset_token");
    }

    private String body(Object o) {
        return json.writeValueAsString(o);
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String pw) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", pw, "remember", false))));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(Cookie c) throws Exception {
        return c == null ? mvc.perform(post("/api/auth/refresh")) : mvc.perform(post("/api/auth/refresh").cookie(c));
    }

    // ------------------------------------------------------------------ sign in

    @Test
    void everyRoleCanSignInAndGetsTheirBranch() throws Exception {
        Object[][] cases = {{OWNER, "OWNER", null}, {SALES, "SALESPERSON", "ella"}, {STAFF, "STAFF", "ella"}, {ACCOUNTANT, "ACCOUNTANT", null}};
        for (Object[] c : cases) {
            MvcResult r = login((String) c[0], PASSWORD).andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.idleMinutes").value(15))
                    .andExpect(jsonPath("$.me.user.role").value(c[1]))
                    .andExpect(jsonPath("$.me.shop.name").value("Ceylon Gem House"))
                    .andExpect(jsonPath("$.me.branches.length()").value(2))
                    .andExpect(jsonPath("$.me.rates.usd").value(300.0))
                    .andExpect(jsonPath("$.me.rates.eur").value(325.0))
                    .andReturn();
            String branch = json.readTree(r.getResponse().getContentAsString()).path("me").path("user").path("branch").asString(null);
            assertThat(branch).isEqualTo(c[2]);
            String cookie = r.getResponse().getHeader("Set-Cookie");
            assertThat(cookie).contains(AuthController.COOKIE + "=").contains("HttpOnly").contains("SameSite=Strict")
                    .contains("Path=/api/auth").doesNotContain("Max-Age"); // not "keep me signed in" = gone when the browser closes
        }
        assertThat(jdbc.queryForObject("select count(*) from app_user where last_login_at > now() - interval '1 minute'", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void keepMeSignedInLastsThirtyDays() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", OWNER, "password", PASSWORD, "remember", true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idleMinutes").value(0)).andReturn();
        assertThat(r.getResponse().getHeader("Set-Cookie")).contains("Max-Age=259");
    }

    @Test
    void emailIgnoresCapitals() throws Exception {
        login("Owner@CeylonGemHouse.LK", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void wrongEmailOrPasswordSaysTheSame() throws Exception {
        login("nobody@ceylongemhouse.lk", PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_LOGIN")).andExpect(jsonPath("$.message").value("Wrong email or password."));
        login(OWNER, "Wrong123").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("BAD_LOGIN"));
    }

    @Test
    void fiveWrongPasswordsLockTheLogin() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(SALES, "Wrong123").andExpect(jsonPath("$.code").value("BAD_LOGIN"));
        }
        login(SALES, "Wrong123").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        login(SALES, PASSWORD).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.message").value(containsString("15 minutes")));
        // after the lock time the right password works again
        jdbc.update("update app_user set locked_until = now() - interval '1 second' where email = ?", SALES);
        login(SALES, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void goodSignInClearsTheWrongTries() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(STAFF, "Wrong123");
        }
        login(STAFF, PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select failed_logins from app_user where email = ?", Integer.class, STAFF)).isZero();
    }

    @Test
    void disabledAccountCannotSignIn() throws Exception {
        jdbc.update("update app_user set active = false where email = ?", STAFF);
        login(STAFF, PASSWORD).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void missingFieldsAreMarked() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    // ------------------------------------------------------------------ token, renew, sign out

    @Test
    void apiNeedsTheLoginToken() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        Signed s = SignIn.login(mvc, OWNER);
        mvc.perform(get("/api/auth/me").with(s.bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(OWNER));
    }

    @Test
    void renewSwapsTheCookieAndAnOldCookieSignsOutEverywhere() throws Exception {
        Signed s = SignIn.login(mvc, OWNER);
        Signed renewed = SignIn.signed(refresh(s.refresh()).andExpect(status().isOk()).andReturn());
        assertThat(renewed.refresh().getValue()).isNotEqualTo(s.refresh().getValue());
        assertThat(renewed.token()).isNotBlank();
        // the old cookie again right away (another tab renewing too) → refused, but the new one keeps working
        refresh(s.refresh()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_ENDED"));
        Signed again = SignIn.signed(refresh(renewed.refresh()).andExpect(status().isOk()).andReturn());
        // the old cookie again later (copied?) → refused, and the newest one stops working too
        jdbc.update("update refresh_token set revoked_at = now() - interval '2 minutes' where revoked_at is not null");
        refresh(s.refresh()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_ENDED"));
        refresh(again.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void renewWithoutCookieIsRefused() throws Exception {
        refresh(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_ENDED"));
    }

    @Test
    void signOutEndsTheCookie() throws Exception {
        Signed s = SignIn.login(mvc, SALES);
        MvcResult r = mvc.perform(post("/api/auth/logout").cookie(s.refresh())).andExpect(status().isNoContent()).andReturn();
        assertThat(r.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        refresh(s.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void fifteenMinutesWithoutUseSignsOutOnSharedComputers() throws Exception {
        Signed s = SignIn.login(mvc, SALES);
        jdbc.update("update refresh_token set last_used_at = now() - interval '20 minutes'");
        refresh(s.refresh()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(containsString("without use")));
    }

    @Test
    void keptSignedInIsNotSignedOutWhenIdle() throws Exception {
        Signed s = SignIn.login(mvc, OWNER, PASSWORD, true);
        jdbc.update("update refresh_token set last_used_at = now() - interval '3 hours'");
        refresh(s.refresh()).andExpect(status().isOk());
    }

    @Test
    void signInEndsAfterTwelveHours() throws Exception {
        Signed s = SignIn.login(mvc, SALES);
        jdbc.update("update refresh_token set expires_at = now() - interval '1 second'");
        refresh(s.refresh()).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ passwords

    @Test
    void changePassword() throws Exception {
        Signed s = SignIn.login(mvc, STAFF);
        Signed other = SignIn.login(mvc, STAFF);
        mvc.perform(post("/api/auth/change-password").with(s.bearer()).cookie(s.refresh()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "Wrong123", "newPassword", "NewPass99"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WRONG_PASSWORD"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
        mvc.perform(post("/api/auth/change-password").with(s.bearer()).cookie(s.refresh()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", PASSWORD, "newPassword", "short"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
        mvc.perform(post("/api/auth/change-password").with(s.bearer()).cookie(s.refresh()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", PASSWORD, "newPassword", "NewPass99"))))
                .andExpect(status().isNoContent());
        login(STAFF, PASSWORD).andExpect(status().isUnauthorized());
        login(STAFF, "NewPass99").andExpect(status().isOk());
        refresh(s.refresh()).andExpect(status().isOk()); // this device stays signed in
        refresh(other.refresh()).andExpect(status().isUnauthorized()); // other devices are signed out
    }

    @Test
    void forgotPasswordEmailsAOneTimeLink() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", "nobody@x.lk"))))
                .andExpect(status().isAccepted());
        verify(mail, never()).send(eq("nobody@x.lk"), anyString(), anyString());

        Signed before = SignIn.login(mvc, OWNER);
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", OWNER))))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq(OWNER), eq("Reset your password"), text.capture());
        assertThat(text.getValue()).contains("30 minutes").contains("only once");
        Matcher m = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)").matcher(text.getValue());
        assertThat(m.find()).isTrue();
        String token = m.group(1);

        reset(token, "weak").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        reset(token, "Fresh2026x").andExpect(status().isNoContent());
        reset(token, "Again2026x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_LINK_INVALID"));
        login(OWNER, "Fresh2026x").andExpect(status().isOk());
        refresh(before.refresh()).andExpect(status().isUnauthorized()); // other devices were signed out
    }

    @Test
    void resetLinkExpires() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", SALES))));
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq(SALES), anyString(), text.capture());
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(text.getValue());
        assertThat(m.find()).isTrue();
        jdbc.update("update password_reset_token set expires_at = now() - interval '1 second'");
        reset(m.group(1), "Fresh2026x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_LINK_INVALID"));
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token, String pw) throws Exception {
        return mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("token", token, "newPassword", pw))));
    }

    // ------------------------------------------------------------------ older sample databases

    @Test
    void olderSampleDatabaseGetsPasswordsAndRates() throws Exception {
        jdbc.update("update app_user set password_hash = null");
        jdbc.update("delete from exchange_rate");
        var added = sampleData.catchUp();
        assertThat(added).containsEntry("branches + users", 4).containsEntry("exchange rates", 30);
        login(SALES, PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.me.rates.usd").value(300.0));
        assertThat(sampleData.catchUp()).isEmpty(); // nothing more to add
    }

    // ------------------------------------------------------------------ demo buttons (local only)

    @Test
    void demoButtonsWorkOnYourOwnComputer() throws Exception {
        mvc.perform(get("/api/auth/demo-users")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
        mvc.perform(post("/api/auth/demo-login").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", ACCOUNTANT))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.me.user.role").value("ACCOUNTANT"));
    }
}
