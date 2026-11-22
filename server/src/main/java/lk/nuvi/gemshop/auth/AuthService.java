package lk.nuvi.gemshop.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.api.AuthDtos.BranchInfo;
import lk.nuvi.gemshop.auth.api.AuthDtos.Me;
import lk.nuvi.gemshop.auth.api.AuthDtos.RatesInfo;
import lk.nuvi.gemshop.auth.api.AuthDtos.Session;
import lk.nuvi.gemshop.auth.api.AuthDtos.ShopInfo;
import lk.nuvi.gemshop.auth.api.AuthDtos.UserInfo;
import lk.nuvi.gemshop.auth.config.AuthProperties;
import lk.nuvi.gemshop.auth.security.TokenService;
import lk.nuvi.gemshop.auth.session.PasswordResetToken;
import lk.nuvi.gemshop.auth.session.PasswordResetTokenRepository;
import lk.nuvi.gemshop.auth.session.RefreshToken;
import lk.nuvi.gemshop.auth.session.RefreshTokenRepository;
import lk.nuvi.gemshop.auth.session.Tokens;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.mail.MailService;
import lk.nuvi.gemshop.common.money.Currency;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.settings.branch.BranchStatus;
import lk.nuvi.gemshop.settings.rates.ExchangeRate;
import lk.nuvi.gemshop.settings.rates.ExchangeRateRepository;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign in, renew, sign out, change and reset password.
 * <ul>
 *   <li>Wrong password 5 times in a row locks the login for 15 minutes (the owner can unlock it).</li>
 *   <li>The login token lasts 10 minutes and is renewed while the person works. Without "Keep me signed in",
 *       15 minutes without use signs the person out (shared shop computers), and 12 hours is the longest sign-in.</li>
 *   <li>The long token is kept in an http-only cookie and changed on every renewal; using an old one again
 *       signs that person out everywhere.</li>
 * </ul>
 */
@Service
public class AuthService {

    public static final String BAD_LOGIN = "BAD_LOGIN";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String SESSION_ENDED = "SESSION_ENDED";
    /** Another tab renewed a moment ago; the browser already has the new cookie, so just try again. */
    public static final String SESSION_RENEWED = "SESSION_RENEWED";
    public static final String WRONG_PASSWORD = "WRONG_PASSWORD";
    public static final String RESET_LINK_INVALID = "RESET_LINK_INVALID";

    /** Two browser tabs may renew at the same moment; an old token used again this soon is not treated as copied. */
    static final long REUSE_GRACE_SECONDS = 60;

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final BranchRepository branches;
    private final ShopSettingsRepository shops;
    private final ExchangeRateRepository rates;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final MailService mail;
    private final AuthProperties props;
    private final ActivityLog activity;

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens,
            BranchRepository branches, ShopSettingsRepository shops, ExchangeRateRepository rates, PasswordEncoder encoder,
            TokenService tokens, MailService mail, AuthProperties props, ActivityLog activity) {
        this.activity = activity;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.branches = branches;
        this.shops = shops;
        this.rates = rates;
        this.encoder = encoder;
        this.tokens = tokens;
        this.mail = mail;
        this.props = props;
    }

    /** A sign-in: the answer for the screen, and the long token for the cookie. */
    public record Result(Session session, String refreshToken, boolean remember, Instant refreshExpiresAt) {}

    // ------------------------------------------------------------------ sign in

    /** Checks email and password. Wrong tries are saved even though the sign-in fails. */
    @Transactional(noRollbackFor = ApiException.class)
    public Result login(String email, String password, boolean remember, String device) {
        Instant now = Instant.now();
        UserAccount u = users.findByEmail(UserAccount.normalise(email)).orElse(null);
        if (u == null) {
            encoder.matches(password, "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BWX4Z1xZ8sOgYJKcHjOt9fJ6O1y."); // same time as a real check
            throw badLogin();
        }
        if (u.isLocked(now)) {
            throw locked();
        }
        if (u.getPasswordHash() == null || !encoder.matches(password, u.getPasswordHash())) {
            boolean nowLocked = u.wrongPassword(props.maxFailedLogins(), now.plus(props.lockMinutes(), ChronoUnit.MINUTES));
            log.info("Wrong password for {}{}", u.getEmail(), nowLocked ? " – login locked" : "");
            if (nowLocked) {
                activity.addSystem("Login locked", u.getName() + " · " + props.maxFailedLogins() + " wrong passwords – locked for "
                        + props.lockMinutes() + " minutes", branchOf(u));
            }
            throw nowLocked ? locked() : badLogin();
        }
        if (!u.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ACCOUNT_DISABLED, "Account disabled. Ask the owner to activate it.");
        }
        return signIn(u, remember, device, now);
    }

    /** Sign-in without password – only for the "Demo: sign in as…" buttons on local and dev computers. */
    @Transactional
    public Result demoLogin(String email, String device) {
        UserAccount u = users.findByEmail(UserAccount.normalise(email)).filter(UserAccount::isActive)
                .orElseThrow(() -> ApiException.notFound("No such demo user."));
        return signIn(u, false, device, Instant.now());
    }

    /** Signs in a person who was just created or checked (first-time setup). */
    @Transactional
    public Result signIn(UserAccount u, boolean remember, String device, Instant now) {
        u.loggedIn(now);
        String raw = Tokens.random();
        Instant exp = remember ? now.plus(props.rememberDays(), ChronoUnit.DAYS) : now.plus(props.sessionHours(), ChronoUnit.HOURS);
        refreshTokens.save(new RefreshToken(u, Tokens.hash(raw), remember, exp, now, device));
        return new Result(session(u, remember), raw, remember, exp);
    }

    // ------------------------------------------------------------------ renew / sign out

    /** Renews the login token with the cookie token, and swaps the cookie token for a new one. */
    @Transactional(noRollbackFor = ApiException.class)
    public Result refresh(String raw, String device) {
        Instant now = Instant.now();
        if (raw == null || raw.isBlank()) {
            throw ended("Please sign in.");
        }
        RefreshToken t = refreshTokens.findByTokenHash(Tokens.hash(raw)).orElseThrow(() -> ended("Please sign in."));
        UserAccount u = t.getUser();
        if (t.getRevokedAt() != null) {
            if (t.isReplaced() && t.getRevokedAt().isBefore(now.minusSeconds(REUSE_GRACE_SECONDS))) {
                // An old token was used again: someone may have copied it. Sign this person out everywhere.
                // (Within a minute it is just two tabs renewing at the same time – the other tab already has the new cookie.)
                log.warn("Old sign-in token used again for {} – signing out everywhere", u.getEmail());
                refreshTokens.revokeAllExcept(u.getId(), -1, now);
                throw ended("Please sign in again.");
            }
            if (t.isReplaced()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, SESSION_RENEWED, "Already renewed in another tab – try again.");
            }
            throw ended("Please sign in again.");
        }
        if (!t.getExpiresAt().isAfter(now)) {
            t.revoke(now, false);
            throw ended("Your sign-in has ended. Please sign in again.");
        }
        if (!t.isRemember() && t.getLastUsedAt().isBefore(now.minus(props.idleMinutes() + 1L, ChronoUnit.MINUTES))) {
            t.revoke(now, false);
            throw ended("You were signed out after " + props.idleMinutes() + " minutes without use.");
        }
        if (!u.isActive()) {
            t.revoke(now, false);
            throw ended("Account disabled. Ask the owner to activate it.");
        }
        t.revoke(now, true);
        String next = Tokens.random();
        Instant exp = t.isRemember() ? now.plus(props.rememberDays(), ChronoUnit.DAYS) : t.getExpiresAt();
        refreshTokens.save(new RefreshToken(u, Tokens.hash(next), t.isRemember(), exp, now, device));
        return new Result(session(u, t.isRemember()), next, t.isRemember(), exp);
    }

    /** Signs out this device. */
    @Transactional
    public void logout(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(Tokens.hash(raw)).ifPresent(t -> t.revoke(Instant.now(), false));
    }

    // ------------------------------------------------------------------ passwords

    /** Change password (user menu). Other devices are signed out; this one stays signed in. */
    @Transactional
    public void changePassword(long userId, String current, String next, String currentRefresh) {
        UserAccount u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        if (u.getPasswordHash() == null || !encoder.matches(current, u.getPasswordHash())) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, WRONG_PASSWORD, "The current password is not right.",
                    "currentPassword", "Not right.");
        }
        PasswordRules.check(next, "newPassword");
        Instant now = Instant.now();
        u.changePassword(encoder.encode(next), now);
        long keep = currentRefresh == null ? -1
                : refreshTokens.findByTokenHash(Tokens.hash(currentRefresh)).map(RefreshToken::getId).orElse(-1L);
        refreshTokens.revokeAllExcept(u.getId(), keep, now);
        activity.addFor(u, "Password changed", "Other devices signed out", branchOf(u));
    }

    /**
     * Forgot password: emails a reset link to an active login. Always answers the same,
     * so nobody can find out which emails have a login.
     */
    @Transactional
    public void forgotPassword(String email) {
        users.findByEmail(UserAccount.normalise(email)).filter(UserAccount::isActive).ifPresent(u -> {
            String raw = Tokens.random();
            resetTokens.save(new PasswordResetToken(u, Tokens.hash(raw),
                    Instant.now().plus(props.resetLinkMinutes(), ChronoUnit.MINUTES)));
            String link = props.appUrl().replaceAll("/+$", "") + "/reset-password?token=" + raw;
            mail.send(u.getEmail(), "Reset your password", """
                    Hello %s,

                    Click the link below to choose a new password:
                    %s

                    The link works for %d minutes and only once.
                    If you did not ask for this, you can ignore this email – your password stays the same.
                    """.formatted(u.getName().split(" ")[0], link, props.resetLinkMinutes()));
        });
    }

    /** Sets a new password with the emailed link. The link then stops working; all devices are signed out. */
    @Transactional
    public void resetPassword(String raw, String next) {
        Instant now = Instant.now();
        PasswordResetToken t = resetTokens.findByTokenHash(Tokens.hash(raw)).filter(x -> x.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest(RESET_LINK_INVALID,
                        "This reset link has expired or was already used. Ask for a new one."));
        PasswordRules.check(next, "newPassword");
        t.use(now);
        UserAccount u = t.getUser();
        u.changePassword(encoder.encode(next), now);
        refreshTokens.revokeAllExcept(u.getId(), -1, now);
        activity.addFor(u, "Password reset", "With the emailed link · all devices signed out", branchOf(u));
    }

    private static Long branchOf(UserAccount u) {
        return u.getBranch() == null ? null : u.getBranch().getId();
    }

    // ------------------------------------------------------------------ who is signed in

    @Transactional(readOnly = true)
    public Me me(long userId) {
        return me(users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found.")));
    }

    Me me(UserAccount u) {
        List<BranchInfo> list = branches.findAll().stream()
                .filter(b -> b.getStatus() != BranchStatus.INACTIVE)
                .sorted(Comparator.comparing(b -> b.getId()))
                .map(b -> new BranchInfo(b.getCode(), b.getName(), b.getShortName(), b.getStatus().name()))
                .toList();
        ExchangeRate usd = rates.current(Currency.USD).orElse(null);
        ExchangeRate eur = rates.current(Currency.EUR).orElse(null);
        Instant updated = usd == null ? null : eur == null ? usd.getEffectiveAt()
                : usd.getEffectiveAt().isAfter(eur.getEffectiveAt()) ? usd.getEffectiveAt() : eur.getEffectiveAt();
        return new Me(
                new UserInfo(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(),
                        u.getBranch() == null ? null : u.getBranch().getCode()),
                new ShopInfo(shops.current().map(s -> s.getName()).orElse("Gem Shop System")),
                list,
                new RatesInfo(usd == null ? null : usd.getRate(), eur == null ? null : eur.getRate(), updated));
    }

    private Session session(UserAccount u, boolean remember) {
        TokenService.AccessToken a = tokens.issue(u);
        return new Session(a.token(), a.expiresAt(), remember ? 0 : props.idleMinutes(), me(u));
    }

    private ApiException badLogin() {
        return new ApiException(HttpStatus.UNAUTHORIZED, BAD_LOGIN, "Wrong email or password.");
    }

    private ApiException locked() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ACCOUNT_LOCKED, "Too many wrong passwords. The login is locked for "
                + props.lockMinutes() + " minutes – or ask the owner to unlock it.");
    }

    private static ApiException ended(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, SESSION_ENDED, message);
    }
}
