package lk.nuvi.gemshop;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import lk.nuvi.gemshop.auth.api.AuthController;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/** Test helpers: sign in as a sample person (password Gem@2026 on local), or a ready-made owner token. */
public final class SignIn {

    public static final String PASSWORD = "Gem@2026";
    public static final String OWNER = "owner@ceylongemhouse.lk";
    public static final String SALES = "nimal@ceylongemhouse.lk";
    public static final String STAFF = "kasun@ceylongemhouse.lk";
    public static final String ACCOUNTANT = "dilani@fernandoassociates.lk";

    /** Token without a database user – for tests that only need "signed in as owner". */
    public static final RequestPostProcessor OWNER_JWT = jwt().jwt(j -> j.subject("1").claim("role", "OWNER"))
            .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SignIn() {}

    public record Signed(String token, Cookie refresh, String body) {
        public RequestPostProcessor bearer() {
            return r -> {
                r.addHeader("Authorization", "Bearer " + token);
                return r;
            };
        }
    }

    public static Signed login(MockMvc mvc, String email, String password, boolean remember) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(java.util.Map.of("email", email, "password", password, "remember", remember))))
                .andExpect(status().isOk()).andReturn();
        return signed(r);
    }

    public static Signed login(MockMvc mvc, String email) throws Exception {
        return login(mvc, email, PASSWORD, false);
    }

    public static Signed signed(MvcResult r) throws Exception {
        String body = r.getResponse().getContentAsString();
        return new Signed(JSON.readTree(body).path("accessToken").asString(), r.getResponse().getCookie(AuthController.COOKIE), body);
    }
}
