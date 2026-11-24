package lk.nuvi.gemshop.auth;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.session.Tokens;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-step sign-in for the owner (P6-01; Security &amp; Data Agreement step 4): after the password, a 6-digit code from an
 * authenticator app on the owner's phone (Google Authenticator, Microsoft Authenticator…). Standard TOTP (RFC 6238:
 * HMAC-SHA1, 30 seconds, 6 digits), so any authenticator app works, without internet on the phone.
 * <ul>
 *   <li>Setting up: {@link #start} makes a secret (shown as a QR code), {@link #confirm} turns it on with the first right
 *       code and gives 8 backup codes, shown once.</li>
 *   <li>Signing in: {@link #check} – a code from now or 30 seconds before / after (phone clock a little off); each code
 *       only once. A backup code works once instead of a phone code.</li>
 *   <li>A lost phone and no backup codes: NUVI switches it off in the database (see the handover notes).</li>
 * </ul>
 */
@Service
public class TwoStep {

    public static final String CODE_NEEDED = "TWO_STEP_CODE_NEEDED";
    public static final String WRONG_CODE = "TWO_STEP_WRONG_CODE";
    static final int BACKUP_CODES = 8;
    private static final long PERIOD = 30;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository users;
    private final ShopSettingsRepository shops;
    private final PasswordEncoder encoder;
    private final ActivityLog activity;

    public TwoStep(UserAccountRepository users, ShopSettingsRepository shops, PasswordEncoder encoder, ActivityLog activity) {
        this.users = users;
        this.shops = shops;
        this.encoder = encoder;
        this.activity = activity;
    }

    public record Status(boolean on, Instant since, int backupCodesLeft, boolean allowed) {}

    public record Setup(String secret, String uri) {}

    @Transactional(readOnly = true)
    public Status status(long userId) {
        UserAccount u = user(userId);
        return new Status(u.isTwoStepOn(), u.getTwoStepOnAt(), backups(u).size(), allowed(u));
    }

    /** A new secret for the authenticator app. Two-step stays off until {@link #confirm}. */
    @Transactional
    public Setup start(long userId) {
        UserAccount u = user(userId);
        if (!allowed(u)) {
            throw ApiException.forbidden("Two-step sign-in is for the owner's login.");
        }
        if (u.isTwoStepOn()) {
            throw ApiException.conflict("TWO_STEP_ON", "Two-step sign-in is already on. Switch it off first to use a new phone.");
        }
        byte[] key = new byte[20];
        RANDOM.nextBytes(key);
        String secret = base32(key);
        u.startTwoStep(secret);
        return new Setup(secret, uri(u, secret));
    }

    /** Turns two-step on with the first right code from the app; returns the backup codes (shown once). */
    @Transactional
    public List<String> confirm(long userId, String code, Instant now) {
        UserAccount u = user(userId);
        if (u.isTwoStepOn()) {
            throw ApiException.conflict("TWO_STEP_ON", "Two-step sign-in is already on.");
        }
        if (u.getTwoStepSecret() == null) {
            throw ApiException.badRequest("TWO_STEP_NOT_STARTED", "Start the set-up again and scan the new QR code.");
        }
        Long step = matchingStep(u.getTwoStepSecret(), digits(code), now, null);
        if (step == null) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, WRONG_CODE, "That code is not right. Check the phone's time and try the newest code.",
                    "code", "6 digits from the app");
        }
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODES; i++) {
            codes.add(backupCode());
        }
        u.twoStepOn(step, String.join(",", codes.stream().map(TwoStep::hashBackup).toList()), now);
        activity.addFor(u, "Two-step sign-in on", u.getName() + " · authenticator app + " + BACKUP_CODES + " backup codes", null);
        return codes;
    }

    /** Switches two-step off (needs the password again). */
    @Transactional
    public void off(long userId, String password) {
        UserAccount u = user(userId);
        if (u.getPasswordHash() == null || !encoder.matches(password == null ? "" : password, u.getPasswordHash())) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, AuthService.WRONG_PASSWORD, "The password is not right.", "password", "Your password");
        }
        boolean was = u.isTwoStepOn();
        u.twoStepOff();
        if (was) {
            activity.addFor(u, "Two-step sign-in off", u.getName(), null);
        }
    }

    /** Sign-in: true when the code (from the app, or an unused backup code) is right; it then cannot be used again. */
    public boolean check(UserAccount u, String code, Instant now) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String d = digits(code);
        if (d.matches("[0-9]{6}")) {
            Long step = matchingStep(u.getTwoStepSecret(), d, now, u.getTwoStepLastStep());
            if (step != null) {
                u.twoStepUsed(step);
                return true;
            }
            return false;
        }
        List<String> left = new ArrayList<>(backups(u));
        if (left.remove(hashBackup(code))) {
            u.twoStepBackupLeft(String.join(",", left));
            activity.addFor(u, "Backup code used", u.getName() + " · " + left.size() + " backup codes left", null);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ TOTP (RFC 6238)

    /** The time step (now, one before or one after) whose code matches, newer than {@code after}; null when none. */
    static Long matchingStep(String secret, String code, Instant now, Long after) {
        if (secret == null || code == null || code.length() != 6) {
            return null;
        }
        byte[] key = unbase32(secret);
        long current = now.getEpochSecond() / PERIOD;
        for (long s = current - 1; s <= current + 1; s++) {
            if ((after == null || s > after) && java.security.MessageDigest.isEqual(code(key, s).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return s;
            }
        }
        return null;
    }

    /** The 6-digit code for one time step (RFC 4226 HOTP with HMAC-SHA1). */
    static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    static byte[] unbase32(String s) {
        String t = s.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        byte[] out = new byte[t.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int i = 0;
        for (char c : t.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("Not base32");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out[i++] = (byte) (buffer >> (bits - 8));
                bits -= 8;
            }
        }
        return Arrays.copyOf(out, i);
    }

    // ------------------------------------------------------------------ helpers

    private String uri(UserAccount u, String secret) {
        String issuer = shops.current().map(x -> x.getName()).filter(n -> n != null && !n.isBlank()).orElse("Gem Shop System");
        return "otpauth://totp/" + enc(issuer) + ":" + enc(u.getEmail()) + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean allowed(UserAccount u) {
        return u.getRole() == Role.OWNER;
    }

    private static List<String> backups(UserAccount u) {
        String b = u.getTwoStepBackup();
        return b == null || b.isBlank() ? List.of() : Arrays.asList(b.split(","));
    }

    /** "k7m2-q9xp": 8 letters / digits without look-alikes (0 / o, 1 / l). */
    private static String backupCode() {
        String abc = "abcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(abc.charAt(RANDOM.nextInt(abc.length())));
        }
        return sb.toString();
    }

    static String hashBackup(String code) {
        return Tokens.hash(code.trim().toLowerCase(Locale.ROOT).replace("-", "").replace(" ", ""));
    }

    private static String digits(String code) {
        return code == null ? "" : code.replaceAll("\\s", "");
    }

    private UserAccount user(long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Login not found."));
    }
}
