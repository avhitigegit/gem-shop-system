package lk.nuvi.gemshop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import lk.nuvi.gemshop.auth.security.RequestLimits;
import lk.nuvi.gemshop.common.crypto.FieldCrypto;
import lk.nuvi.gemshop.customers.CustomerRepository;
import lk.nuvi.gemshop.customers.EncryptCustomerContacts;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

/**
 * P6-01 security check: no API answers without login, customer contact details are encrypted in the database, too many
 * sign-in tries from one address are stopped, and the production settings hide Swagger, demo logins and sample data.
 * (Roles on every API: {@link EndpointRolesTest}; branches: {@link RoleAccessTest}; lockout: {@link AuthTest}; uploads:
 * {@code FilesTest}.) Own database, because it writes plain-text contacts to check they get encrypted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=security")
class SecurityTest {

    /** Same list as SecurityConfig.PUBLIC: calls that work without login. */
    private static final List<String> PUBLIC = List.of("/api/health", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
            "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/demo-users", "/api/auth/demo-login", "/api/setup/**",
            "/api/setup", "/api/files/*/content", "/api/public/**");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource db;
    @Autowired FieldCrypto crypto;
    @Autowired CustomerRepository customers;
    @Autowired EncryptCustomerContacts encryptContacts;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Test
    void noApiAnswersWithoutLogin() throws Exception {
        AntPathMatcher match = new AntPathMatcher();
        List<String> open = new ArrayList<>();
        int checked = 0;
        for (var info : mapping.getHandlerMethods().keySet()) {
            var methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/") || pattern.startsWith("/api/test/")
                        || PUBLIC.stream().anyMatch(p -> match.match(p, pattern))) {
                    continue;
                }
                String path = pattern.replaceAll("\\{[^}]+}", "1");
                for (var m : methods.isEmpty() ? List.of(org.springframework.web.bind.annotation.RequestMethod.GET) : methods) {
                    int code = mvc.perform(request(HttpMethod.valueOf(m.name()), path)).andReturn().getResponse().getStatus();
                    checked++;
                    if (code != 401) {
                        open.add(m + " " + pattern + " → " + code);
                    }
                }
            }
        }
        assertThat(checked).isGreaterThan(100);
        assertThat(open).as("APIs that answer without a login").isEmpty();
    }

    @Test
    void customerContactsAreEncryptedInTheDatabase() {
        var rows = jdbc.queryForList("select code, email, whatsapp, passport from customer where email is not null and whatsapp is not null");
        assertThat(rows).isNotEmpty();
        for (var r : rows) {
            assertThat((String) r.get("email")).startsWith("v1:").doesNotContain("@");
            assertThat((String) r.get("whatsapp")).startsWith("v1:");
            if (r.get("passport") != null) {
                assertThat((String) r.get("passport")).startsWith("v1:");
            }
        }
        // …and the app still reads them as normal text
        String code = (String) rows.get(0).get("code");
        var c = customers.findByCode(code).orElseThrow();
        assertThat(c.getEmail()).contains("@");
        assertThat(c.getWhatsapp()).matches("^[+0-9 ()-]+$");
    }

    @Test
    void plainContactsFromBeforeTheUpdateAreEncryptedOnStart() throws Exception {
        jdbc.update("update customer set email = 'old.plain@example.com', whatsapp = '+49 151 000 111' where code = 'C-004'");
        try (Connection con = db.getConnection()) {
            Context ctx = mock(Context.class);
            when(ctx.getConnection()).thenReturn(con);
            encryptContacts.handle(Event.AFTER_MIGRATE, ctx);
            encryptContacts.handle(Event.AFTER_MIGRATE, ctx); // a second start changes nothing
        }
        var row = jdbc.queryForMap("select email, whatsapp from customer where code = 'C-004'");
        assertThat((String) row.get("email")).startsWith("v1:");
        assertThat(crypto.decrypt((String) row.get("email"))).isEqualTo("old.plain@example.com");
        assertThat(crypto.decrypt((String) row.get("whatsapp"))).isEqualTo("+49 151 000 111");
    }

    @Test
    void tooManySignInTriesFromOneAddressAreStopped() throws Exception {
        RequestLimits limits = new RequestLimits(JsonMapper.builder().build(), true);
        for (int i = 0; i < 20; i++) {
            assertThat(call(limits, "/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse stopped = call(limits, "/api/auth/login", "10.0.0.1");
        assertThat(stopped.getStatus()).isEqualTo(429);
        assertThat(stopped.getHeader("Retry-After")).isNotBlank();
        assertThat(stopped.getContentAsString()).contains("TOO_MANY_REQUESTS").contains("Please wait");
        // another address, and other calls, are not affected
        assertThat(call(limits, "/api/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
        assertThat(call(limits, "/api/sales", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void theLimitStartsAgainAfterItsTime() {
        RequestLimits limits = new RequestLimits(JsonMapper.builder().build(), true);
        var forgot = limitRule("forgot");
        Instant t = Instant.parse("2026-11-24T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            assertThat(take(limits, forgot, t)).isZero();
        }
        assertThat(take(limits, forgot, t.plusSeconds(60))).isEqualTo(Duration.ofMinutes(14).toSeconds());
        assertThat(take(limits, forgot, t.plus(Duration.ofMinutes(15)))).isZero();
    }

    @Test
    void productionHidesSwaggerDemoLoginsAndSampleData() {
        Properties prod = yaml("application-prod.yml");
        assertThat(prod.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(prod.getProperty("app.sample-data.enabled")).isEqualTo("false");
        Properties base = yaml("application.yml");
        assertThat(base.getProperty("app.demo-logins", base.getProperty("app.auth.demo-logins"))).isEqualTo("false");
        assertThat(base.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,info");
        assertThat(base.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        for (String env : new String[] {"application-prod.yml", "application-uat.yml"}) {
            Properties p = yaml(env);
            assertThat(p.stringPropertyNames()).as(env).noneMatch(k -> k.endsWith("demo-logins") && "true".equals(p.getProperty(k)));
        }
    }

    private static MockHttpServletResponse call(RequestLimits limits, String path, String address) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr(address);
        MockHttpServletResponse res = new MockHttpServletResponse();
        limits.doFilter(req, res, new MockFilterChain());
        return res;
    }

    private static Object limitRule(String name) {
        try {
            var f = RequestLimits.class.getDeclaredField("RULES");
            f.setAccessible(true);
            return ((List<?>) f.get(null)).stream().filter(r -> r.toString().contains("name=" + name)).findFirst().orElseThrow();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long take(RequestLimits limits, Object rule, Instant now) {
        try {
            var m = Arrays.stream(RequestLimits.class.getDeclaredMethods()).filter(x -> x.getName().equals("take")).findFirst().orElseThrow();
            m.setAccessible(true);
            return (long) m.invoke(limits, rule, "10.0.0.9", now);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Properties yaml(String file) {
        YamlPropertiesFactoryBean y = new YamlPropertiesFactoryBean();
        y.setResources(new ClassPathResource(file));
        return y.getObject();
    }
}
