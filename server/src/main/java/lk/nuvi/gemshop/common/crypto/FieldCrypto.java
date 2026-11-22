package lk.nuvi.gemshop.common.crypto;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lk.nuvi.gemshop.common.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Encryption of sensitive customer fields – passport, phone, email (tech stack: data protection; PDPA).
 * <ul>
 *   <li>{@link #encrypt}/{@link #decrypt}: AES-256-GCM. Stored as {@code v1:<base64(iv + data)>}. The same text
 *       gives a different value every time, so nobody can see which customers share a passport.</li>
 *   <li>{@link #index}: a keyed hash of the normalised value, to find a customer by email or phone without
 *       decrypting every row (store it in a separate column, e.g. {@code email_index}).</li>
 *   <li>{@link #sign}: signature for private download links.</li>
 * </ul>
 * Use on an entity field: {@code @Convert(converter = EncryptedString.class)} (column type {@code text}).
 * <p>The key comes from {@code app.crypto.key} (ENCRYPTION_KEY, 32+ characters). If the key is lost, encrypted
 * data cannot be read again – also not from a backup.
 */
@Component
public class FieldCrypto {

    private static final String VERSION = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static volatile FieldCrypto instance;

    private final SecretKeySpec encKey;
    private final SecretKeySpec indexKey;
    private final SecretKeySpec linkKey;
    private final SecureRandom random = new SecureRandom();

    public FieldCrypto(AppProperties app) {
        String key = app.crypto().key();
        if (key == null || key.length() < 32) {
            throw new IllegalStateException("app.crypto.key (ENCRYPTION_KEY) must be at least 32 characters");
        }
        if (("prod".equalsIgnoreCase(app.environment()) || "uat".equalsIgnoreCase(app.environment()))
                && key.startsWith("local-only")) {
            throw new IllegalStateException("Set a real ENCRYPTION_KEY on this server");
        }
        this.encKey = new SecretKeySpec(derive(key, "encrypt"), "AES");
        this.indexKey = new SecretKeySpec(derive(key, "index"), "HmacSHA256");
        this.linkKey = new SecretKeySpec(derive(key, "links"), "HmacSHA256");
    }

    /** Only the app's own instance (made by Spring) is used by {@link EncryptedString}. */
    @PostConstruct
    void register() {
        instance = this;
    }

    /** For JPA converters, which Hibernate creates itself. */
    static FieldCrypto get() {
        FieldCrypto c = instance;
        if (c == null) {
            throw new IllegalStateException("FieldCrypto is not ready yet");
        }
        return c;
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, encKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] data = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + data.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(data, 0, out, iv.length, data.length);
            return VERSION + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(VERSION)) {
            throw new IllegalStateException("Value is not encrypted");
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(VERSION.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, encKey, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            return new String(c.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot decrypt a value – wrong ENCRYPTION_KEY?", e);
        }
    }

    /** Search key for an email (lower case, trimmed). Null for an empty value. */
    public String emailIndex(String email) {
        return email == null || email.isBlank() ? null : index(email.trim().toLowerCase(Locale.ROOT));
    }

    /** Search key for a phone / WhatsApp number (digits only, so "+94 77 123 4567" = "+94771234567"). */
    public String phoneIndex(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.isEmpty() ? null : index(digits);
    }

    /** Search key for a passport number (upper case, no spaces). */
    public String passportIndex(String passport) {
        if (passport == null) {
            return null;
        }
        String p = passport.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        return p.isEmpty() ? null : index(p);
    }

    public String index(String normalised) {
        return HexFormat.of().formatHex(hmac(indexKey, normalised));
    }

    /** Signature for a private link (URL-safe). */
    public String sign(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(linkKey, text));
    }

    public boolean verify(String text, String signature) {
        if (signature == null) {
            return false;
        }
        return MessageDigest.isEqual(sign(text).getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] hmac(SecretKeySpec key, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] derive(String key, String purpose) {
        try {
            return MessageDigest.getInstance("SHA-256").digest((purpose + ":" + key).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
