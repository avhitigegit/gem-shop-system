package lk.nuvi.gemshop.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lk.nuvi.gemshop.auth.AuthService;
import lk.nuvi.gemshop.auth.api.AuthDtos.ChangePasswordRequest;
import lk.nuvi.gemshop.auth.api.AuthDtos.DemoLoginRequest;
import lk.nuvi.gemshop.auth.api.AuthDtos.DemoUser;
import lk.nuvi.gemshop.auth.api.AuthDtos.ForgotPasswordRequest;
import lk.nuvi.gemshop.auth.api.AuthDtos.LoginRequest;
import lk.nuvi.gemshop.auth.api.AuthDtos.Me;
import lk.nuvi.gemshop.auth.api.AuthDtos.Message;
import lk.nuvi.gemshop.auth.api.AuthDtos.ResetPasswordRequest;
import lk.nuvi.gemshop.auth.api.AuthDtos.Session;
import lk.nuvi.gemshop.auth.config.AuthProperties;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.PublicApi;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Login screens (prototype auth/login.html, auth/forgot-password.html) and the user menu. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Login")
public class AuthController {

    /** The long sign-in token: http-only (scripts cannot read it), only sent to /api/auth. */
    public static final String COOKIE = "gs_refresh";
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService auth;
    private final CurrentUser current;
    private final AuthProperties props;
    private final AppProperties app;
    private final UserAccountRepository users;

    public AuthController(AuthService auth, CurrentUser current, AuthProperties props, AppProperties app, UserAccountRepository users) {
        this.auth = auth;
        this.current = current;
        this.props = props;
        this.app = app;
        this.users = users;
    }

    @PublicApi
    @PostMapping("/login")
    @Operation(operationId = "login", summary = "Sign in with email and password")
    public Session login(@Valid @RequestBody LoginRequest body, HttpServletRequest req, HttpServletResponse res) {
        return send(auth.login(body.email(), body.password(), body.keep(), device(req)), res);
    }

    @PublicApi
    @PostMapping("/refresh")
    @Operation(operationId = "refreshSession", summary = "Renew the login token (uses the sign-in cookie)")
    public Session refresh(@CookieValue(name = COOKIE, required = false) String token, HttpServletRequest req, HttpServletResponse res) {
        try {
            return send(auth.refresh(token, device(req)), res);
        } catch (ApiException e) {
            if (!AuthService.SESSION_RENEWED.equals(e.getCode())) {
                clear(res); // keep the cookie when another tab just put a new one in it
            }
            throw e;
        }
    }

    @PublicApi
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "logout", summary = "Sign out on this device")
    public void logout(@CookieValue(name = COOKIE, required = false) String token, HttpServletResponse res) {
        auth.logout(token);
        clear(res);
    }

    @PreAuthorize(Roles.ANY)
    @GetMapping("/me")
    @Operation(operationId = "getMe", summary = "Who is signed in, the shop name, branches and today's rates")
    public Me me() {
        return auth.me(current.require().id());
    }

    @PreAuthorize(Roles.ANY)
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "changePassword", summary = "Change my password (other devices are signed out)")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body,
            @CookieValue(name = COOKIE, required = false) String token) {
        auth.changePassword(current.require().id(), body.currentPassword(), body.newPassword(), token);
    }

    @PublicApi
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "forgotPassword", summary = "Email a reset link (same answer whether the email has a login or not)")
    public Message forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
        auth.forgotPassword(body.email());
        return new Message("If this email has a login, a reset link was sent.");
    }

    @PublicApi
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "resetPassword", summary = "Choose a new password with the emailed link")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        auth.resetPassword(body.token(), body.newPassword());
    }

    @PublicApi
    @GetMapping("/demo-users")
    @Operation(operationId = "getDemoUsers", summary = "Sample people for the demo buttons (local and dev only)")
    public List<DemoUser> demoUsers() {
        requireDemo();
        return users.findAll().stream().filter(u -> u.isActive())
                .sorted(java.util.Comparator.comparing(u -> u.getId()))
                .map(u -> new DemoUser(u.getName(), u.getEmail(), u.getRole())).toList();
    }

    @PublicApi
    @PostMapping("/demo-login")
    @Operation(operationId = "demoLogin", summary = "Sign in as a sample person without password (local and dev only)")
    public Session demoLogin(@Valid @RequestBody DemoLoginRequest body, HttpServletRequest req, HttpServletResponse res) {
        requireDemo();
        return send(auth.demoLogin(body.email(), device(req)), res);
    }

    private void requireDemo() {
        String env = app.environment();
        if (!props.demoLogins() || "prod".equalsIgnoreCase(env) || "uat".equalsIgnoreCase(env)) {
            throw ApiException.notFound("Not found.");
        }
    }

    /** Puts the long token in the cookie and returns the answer for the screen. */
    public Session send(AuthService.Result r, HttpServletResponse res) {
        ResponseCookie.ResponseCookieBuilder c = ResponseCookie.from(COOKIE, r.refreshToken())
                .httpOnly(true).secure(props.cookieSecure()).sameSite("Strict").path(COOKIE_PATH);
        if (r.remember()) {
            c.maxAge(Duration.between(Instant.now(), r.refreshExpiresAt()));
        }
        res.addHeader(HttpHeaders.SET_COOKIE, c.build().toString());
        return r.session();
    }

    private void clear(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, "").httpOnly(true).secure(props.cookieSecure())
                .sameSite("Strict").path(COOKIE_PATH).maxAge(0).build().toString());
    }

    private static String device(HttpServletRequest req) {
        return req.getHeader(HttpHeaders.USER_AGENT);
    }
}
