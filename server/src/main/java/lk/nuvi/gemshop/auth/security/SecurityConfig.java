package lk.nuvi.gemshop.auth.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lk.nuvi.gemshop.auth.config.AuthProperties;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiError;
import lk.nuvi.gemshop.common.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

/**
 * Login check for every API: a valid login token (JWT) is needed for everything under /api,
 * except the {@link PublicApi} calls below. Each API then checks the role with @PreAuthorize
 * and the branch with {@link CurrentUser}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Calls that work without login. */
    static final String[] PUBLIC = {
        "/api/health",
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/logout",
        "/api/auth/forgot-password",
        "/api/auth/reset-password",
        "/api/auth/demo-users",
        "/api/auth/demo-login",
        "/api/setup/**"
    };

    @Bean
    SecurityFilterChain api(HttpSecurity http, ObjectMapper json, JwtDecoder decoder) throws Exception {
        http.csrf(c -> c.disable()) // the token is sent in a header; the sign-in cookie is SameSite=Strict
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(roles()))
                        .authenticationEntryPoint((req, res, e) -> write(json, res, req.getRequestURI(), HttpStatus.UNAUTHORIZED,
                                ErrorCode.UNAUTHORIZED, "Please sign in again."))
                        .accessDeniedHandler((req, res, e) -> write(json, res, req.getRequestURI(), HttpStatus.FORBIDDEN,
                                ErrorCode.FORBIDDEN, "Your login cannot do this.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(json, res, req.getRequestURI(), HttpStatus.UNAUTHORIZED,
                                ErrorCode.UNAUTHORIZED, "Please sign in."))
                        .accessDeniedHandler((req, res, ex) -> write(json, res, req.getRequestURI(), HttpStatus.FORBIDDEN,
                                ErrorCode.FORBIDDEN, "Your login cannot do this.")));
        return http.build();
    }

    private static JwtAuthenticationConverter roles() {
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(jwt -> List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(TokenService.ROLE))));
        return c;
    }

    private static void write(ObjectMapper json, jakarta.servlet.http.HttpServletResponse res, String path, HttpStatus status,
            String code, String message) throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(res.getOutputStream(),
                new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path, List.of()));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtKey(AuthProperties auth, AppProperties app) {
        String secret = auth.jwtSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("app.auth.jwt-secret (JWT_SECRET) must be at least 32 characters");
        }
        if ("prod".equalsIgnoreCase(app.environment()) && secret.startsWith("local-only")) {
            throw new IllegalStateException("Set a real JWT_SECRET in production");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
