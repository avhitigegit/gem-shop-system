package lk.nuvi.gemshop.notifications;

import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Web Push with the JDK only: message encryption (RFC 8291, "aes128gcm") and the VAPID sign-in to the push service
 * (RFC 8292, ES256 JWT). Keys are P-256; a public key is the 65-byte uncompressed point, base64url without padding.
 */
public final class WebPushCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder D64 = Base64.getUrlDecoder();
    private static final int RECORD_SIZE = 4096;

    private WebPushCrypto() {}

    public static String b64(byte[] b) {
        return B64.encodeToString(b);
    }

    public static byte[] unb64(String s) {
        return D64.decode(s.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    // ------------------------------------------------------------------ keys

    public static KeyPair newKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 0x04 || X || Y (65 bytes). */
    public static byte[] raw(ECPublicKey k) {
        byte[] out = new byte[65];
        out[0] = 4;
        copy32(k.getW().getAffineX(), out, 1);
        copy32(k.getW().getAffineY(), out, 33);
        return out;
    }

    public static ECPublicKey publicKey(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 65 || raw[0] != 4) {
            throw new GeneralSecurityException("Not an uncompressed P-256 key");
        }
        ECPoint p = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)), new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(p, params()));
    }

    public static PrivateKey privateKey(byte[] pkcs8) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    private static ECParameterSpec params() {
        return ((ECPublicKey) newKeyPair().getPublic()).getParams();
    }

    private static void copy32(BigInteger v, byte[] out, int at) {
        byte[] b = v.toByteArray();
        int len = Math.min(b.length, 32);
        System.arraycopy(b, b.length - len, out, at + 32 - len, len);
    }

    // ------------------------------------------------------------------ RFC 8291

    /**
     * Encrypts a message for one browser subscription.
     *
     * @param uaPublic the subscription's p256dh key (65 bytes)
     * @param auth     the subscription's auth secret (16 bytes)
     * @return the request body (salt, record size, sender key, cipher text)
     */
    public static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] auth) throws GeneralSecurityException {
        KeyPair as = newKeyPair();
        byte[] asPublic = raw((ECPublicKey) as.getPublic());
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] ecdh = ecdh(as.getPrivate(), publicKey(uaPublic));
        byte[][] keys = contentKeys(ecdh, auth, uaPublic, asPublic, salt);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys[0], "AES"), new GCMParameterSpec(128, keys[1]));
        byte[] plain = Arrays.copyOf(payload, payload.length + 1);
        plain[payload.length] = 2; // last (only) record
        byte[] cipher = c.doFinal(plain);
        return ByteBuffer.allocate(16 + 4 + 1 + 65 + cipher.length).put(salt).putInt(RECORD_SIZE).put((byte) 65).put(asPublic).put(cipher)
                .array();
    }

    /** The browser side of {@link #encrypt} – used by the tests to read what was sent. */
    public static byte[] decrypt(byte[] body, PrivateKey uaPrivate, byte[] uaPublic, byte[] auth) throws GeneralSecurityException {
        ByteBuffer b = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        b.get(salt);
        b.getInt();
        byte[] asPublic = new byte[b.get()];
        b.get(asPublic);
        byte[] cipher = new byte[b.remaining()];
        b.get(cipher);
        byte[][] keys = contentKeys(ecdh(uaPrivate, publicKey(asPublic)), auth, uaPublic, asPublic, salt);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys[0], "AES"), new GCMParameterSpec(128, keys[1]));
        byte[] plain = c.doFinal(cipher);
        int end = plain.length - 1;
        while (end >= 0 && plain[end] == 0) {
            end--;
        }
        return Arrays.copyOf(plain, end); // drop the delimiter
    }

    private static byte[] ecdh(PrivateKey mine, ECPublicKey theirs) throws GeneralSecurityException {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(mine);
        ka.doPhase(theirs, true);
        return ka.generateSecret();
    }

    /** {CEK (16), NONCE (12)}. */
    private static byte[][] contentKeys(byte[] ecdh, byte[] auth, byte[] uaPublic, byte[] asPublic, byte[] salt)
            throws GeneralSecurityException {
        byte[] prkKey = hmac(auth, ecdh);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[] {1});
        byte[] ikm = hmac(prkKey, keyInfo);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 12);
        return new byte[][] {cek, nonce};
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        return m.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) {
            n += p.length;
        }
        ByteBuffer b = ByteBuffer.allocate(n);
        for (byte[] p : parts) {
            b.put(p);
        }
        return b.array();
    }

    // ------------------------------------------------------------------ RFC 8292

    /** {@code Authorization} header value for a push endpoint: {@code vapid t=<jwt>, k=<public key>}. */
    public static String vapid(String endpoint, String subject, PrivateKey key, String publicKey, Instant now) throws GeneralSecurityException {
        URI u = URI.create(endpoint);
        String aud = u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = b64(("{\"aud\":\"" + aud + "\",\"exp\":" + now.plusSeconds(12 * 3600).getEpochSecond() + ",\"sub\":\""
                + subject.replace("\"", "") + "\"}").getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initSign(key);
        s.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return "vapid t=" + header + "." + claims + "." + b64(s.sign()) + ", k=" + publicKey;
    }
}
