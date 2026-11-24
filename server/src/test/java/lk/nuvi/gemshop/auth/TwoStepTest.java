package lk.nuvi.gemshop.auth;

import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.PASSWORD;
import static lk.nuvi.gemshop.SignIn.SALES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P6-01: two-step sign-in for the owner – set up, sign in with the phone code or a backup code, wrong codes, switch off. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=twostep")
class TwoStepTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void reset() {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ?, two_step_on = false,"
                + " two_step_secret = null, two_step_last_step = null, two_step_backup = null, two_step_on_at = null", encoder.encode(PASSWORD));
    }

    @Test
    void codesFollowTheStandard() {
        // RFC 6238 test value: secret "12345678901234567890", time 59 s → 94287082 (last 6 digits)
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(TwoStep.code(key, 59 / 30)).isEqualTo("287082");
        assertThat(TwoStep.code(key, 1111111109L / 30)).isEqualTo("081804");
        assertThat(TwoStep.unbase32(TwoStep.base32(key))).isEqualTo(key);
        assertThat(TwoStep.base32(key)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    }

    @Test
    void ownerTurnsItOnAndThenNeedsTheCode() throws Exception {
        Signed owner = SignIn.login(mvc, OWNER);
        mvc.perform(get("/api/auth/two-step").with(owner.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(false)).andExpect(jsonPath("$.allowed").value(true));

        JsonNode setup = read(mvc.perform(post("/api/auth/two-step/start").with(owner.bearer())).andExpect(status().isOk()));
        String secret = setup.path("secret").asString();
        assertThat(setup.path("uri").asString()).startsWith("otpauth://totp/").contains("secret=" + secret).contains("ceylongemhouse.lk");
        assertThat(jdbc.queryForObject("select two_step_secret from app_user where email = ?", String.class, OWNER))
                .as("secret is encrypted").startsWith("v1:").doesNotContain(secret);

        // still off until the first code is right
        SignIn.login(mvc, OWNER);
        confirm(owner, "000000").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(TwoStep.WRONG_CODE));

        long step = Instant.now().getEpochSecond() / 30;
        JsonNode codes = read(confirm(owner, code(secret, step)).andExpect(status().isOk()));
        assertThat(codes.path("codes")).hasSize(8);
        String backup = codes.path("codes").get(0).asString();
        assertThat(backup).matches("[a-z2-9]{4}-[a-z2-9]{4}");

        // password alone is not enough now
        login(OWNER, null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(TwoStep.CODE_NEEDED));
        // the code used to turn it on cannot be used again
        login(OWNER, code(secret, step)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(TwoStep.WRONG_CODE));
        // the next code (phone 30 s ahead) works, once
        login(OWNER, code(secret, step + 1)).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        login(OWNER, code(secret, step + 1)).andExpect(status().isUnauthorized());
        // a wrong password is still a wrong password, code or not
        login(OWNER, "WrongPass1", code(secret, step + 1)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthService.BAD_LOGIN));

        // a backup code works once
        login(OWNER, backup.toUpperCase()).andExpect(status().isOk());
        login(OWNER, backup).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(TwoStep.WRONG_CODE));
        Signed again = SignIn.signed(login(OWNER, codes.path("codes").get(1).asString()).andExpect(status().isOk()).andReturn());
        mvc.perform(get("/api/auth/two-step").with(again.bearer())).andExpect(jsonPath("$.on").value(true))
                .andExpect(jsonPath("$.backupCodesLeft").value(6));
        assertThat(jdbc.queryForObject("select count(*) from activity_log where action = 'Two-step sign-in on'", Integer.class)).isPositive();

        // switching off needs the password
        mvc.perform(post("/api/auth/two-step/off").with(again.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"nope\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/two-step/off").with(again.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("password", PASSWORD)))).andExpect(status().isNoContent());
        SignIn.login(mvc, OWNER); // password alone again
    }

    @Test
    void wrongCodesLockTheLogin() throws Exception {
        Signed owner = SignIn.login(mvc, OWNER);
        String secret = read(mvc.perform(post("/api/auth/two-step/start").with(owner.bearer()))).path("secret").asString();
        confirm(owner, code(secret, Instant.now().getEpochSecond() / 30)).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login(OWNER, "111111").andExpect(jsonPath("$.code").value(TwoStep.WRONG_CODE));
        }
        login(OWNER, "111111").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(AuthService.ACCOUNT_LOCKED));
        login(OWNER, code(secret, Instant.now().getEpochSecond() / 30 + 1)).andExpect(jsonPath("$.code").value(AuthService.ACCOUNT_LOCKED));
    }

    @Test
    void onlyTheOwnerSetsItUp() throws Exception {
        Signed sales = SignIn.login(mvc, SALES);
        mvc.perform(get("/api/auth/two-step").with(sales.bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
        mvc.perform(post("/api/auth/two-step/start").with(sales.bearer())).andExpect(status().isForbidden());
        SignIn.login(mvc, SALES); // staff logins are unchanged
    }

    private ResultActions confirm(Signed s, String code) throws Exception {
        return mvc.perform(post("/api/auth/two-step/confirm").with(s.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("code", code))));
    }

    private ResultActions login(String email, String code) throws Exception {
        return login(email, PASSWORD, code);
    }

    private ResultActions login(String email, String password, String code) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("email", email, "password", password));
        if (code != null) {
            body.put("code", code);
        }
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)));
    }

    private static String code(String secret, long step) {
        return TwoStep.code(TwoStep.unbase32(secret), step);
    }

    private static JsonNode read(ResultActions r) throws Exception {
        return JSON.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
