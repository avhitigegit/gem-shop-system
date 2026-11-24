package lk.nuvi.gemshop.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lk.nuvi.gemshop.common.error.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Limits how often one address may call the calls that work without login (P6-01 security check): sign in, forgot /
 * reset password, first-time setup, demo login and the public links. Wrong passwords already lock the account
 * ({@code AuthService}); this also stops trying many accounts, and filling mailboxes with reset emails.
 * <p>
 * Counted per address in a fixed time window, in memory (one server). Behind Nginx the address comes from
 * X-Forwarded-For ({@code server.forward-headers-strategy: framework}); only Nginx can reach the backend.
 * Answer: 429 {@code TOO_MANY_REQUESTS} with Retry-After. Switched off in tests ({@code app.limits.enabled=false}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLimits extends OncePerRequestFilter {

    public static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";
    private static final Logger LOG = LoggerFactory.getLogger(RequestLimits.class);
    private static final int MAX_KEYS = 20_000;

    /** One rule: calls starting with {@code path} (POST only when {@code postOnly}), at most {@code max} per {@code window}. */
    record Rule(String name, String path, boolean postOnly, int max, Duration window) {
        boolean matches(HttpServletRequest req) {
            return req.getRequestURI().startsWith(path) && (!postOnly || "POST".equals(req.getMethod()));
        }
    }

    static final List<Rule> RULES = List.of(
            new Rule("login", "/api/auth/login", true, 20, Duration.ofMinutes(5)),
            new Rule("forgot", "/api/auth/forgot-password", true, 5, Duration.ofMinutes(15)),
            new Rule("reset", "/api/auth/reset-password", true, 10, Duration.ofMinutes(15)),
            new Rule("demo", "/api/auth/demo-login", true, 30, Duration.ofMinutes(5)),
            new Rule("setup", "/api/setup", true, 10, Duration.ofMinutes(15)),
            new Rule("public", "/api/public/", false, 120, Duration.ofMinutes(1)));

    private record Window(Instant start, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper json;
    private final boolean enabled;

    public RequestLimits(ObjectMapper json, @Value("${app.limits.enabled:true}") boolean enabled) {
        this.json = json;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !enabled || !req.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        for (Rule r : RULES) {
            if (r.matches(req)) {
                long wait = take(r, req.getRemoteAddr(), Instant.now());
                if (wait > 0) {
                    LOG.warn("Too many {} calls from {}", r.name(), req.getRemoteAddr());
                    tooMany(req, res, wait);
                    return;
                }
                break;
            }
        }
        chain.doFilter(req, res);
    }

    /** Counts one call; returns 0 when allowed, otherwise the seconds to wait. */
    long take(Rule r, String address, Instant now) {
        if (windows.size() > MAX_KEYS) {
            windows.entrySet().removeIf(e -> e.getValue().start().plus(Duration.ofMinutes(15)).isBefore(now));
        }
        Window w = windows.compute(r.name() + "|" + address, (k, old) -> old == null || !now.isBefore(old.start().plus(r.window()))
                ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        if (w.count() <= r.max()) {
            return 0;
        }
        return Math.max(1, Duration.between(now, w.start().plus(r.window())).toSeconds());
    }

    private void tooMany(HttpServletRequest req, HttpServletResponse res, long wait) throws IOException {
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After", String.valueOf(wait));
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        long minutes = Math.max(1, (wait + 59) / 60);
        json.writeValue(res.getOutputStream(), new ApiError(Instant.now(), 429, HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                TOO_MANY_REQUESTS, "Too many tries. Please wait " + minutes + " minute(s) and try again.", req.getRequestURI(), List.of()));
    }
}
