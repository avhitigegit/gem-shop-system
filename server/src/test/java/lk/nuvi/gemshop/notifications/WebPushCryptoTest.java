package lk.nuvi.gemshop.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** RFC 8291 message encryption and RFC 8292 VAPID header, without a server. */
class WebPushCryptoTest {

    @Test
    void theBrowserCanReadWhatWasSent() throws Exception {
        KeyPair browser = WebPushCrypto.newKeyPair();
        byte[] uaPublic = WebPushCrypto.raw((ECPublicKey) browser.getPublic());
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        byte[] msg = "{\"title\":\"New sale\",\"body\":\"Sale INV-000150 · LKR 352,000 · France ✓\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(msg, uaPublic, auth);
        assertThat(body.length).isEqualTo(16 + 4 + 1 + 65 + msg.length + 1 + 16);   // header + text + delimiter + tag
        assertThat(body[20]).isEqualTo((byte) 65);
        assertThat(WebPushCrypto.decrypt(body, browser.getPrivate(), uaPublic, auth)).isEqualTo(msg);
        // a fresh key and salt every time
        assertThat(WebPushCrypto.encrypt(msg, uaPublic, auth)).isNotEqualTo(body);
    }

    @Test
    void rawKeysRoundTrip() throws Exception {
        KeyPair k = WebPushCrypto.newKeyPair();
        byte[] raw = WebPushCrypto.raw((ECPublicKey) k.getPublic());
        assertThat(raw).hasSize(65);
        assertThat(WebPushCrypto.publicKey(raw)).isEqualTo(k.getPublic());
        assertThat(WebPushCrypto.unb64(WebPushCrypto.b64(raw))).isEqualTo(raw);
        assertThat(WebPushCrypto.privateKey(k.getPrivate().getEncoded())).isEqualTo(k.getPrivate());
    }

    @Test
    void vapidHeaderIsASignedJwtForThePushService() throws Exception {
        KeyPair k = WebPushCrypto.newKeyPair();
        String pub = WebPushCrypto.b64(WebPushCrypto.raw((ECPublicKey) k.getPublic()));
        String h = WebPushCrypto.vapid("https://fcm.googleapis.com/fcm/send/abc123", "mailto:shop@example.com", k.getPrivate(), pub,
                Instant.ofEpochSecond(1_800_000_000L));
        assertThat(h).startsWith("vapid t=").endsWith(", k=" + pub);
        String jwt = h.substring(8, h.indexOf(','));
        String[] parts = jwt.split("\\.");
        String claims = new String(WebPushCrypto.unb64(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"").contains("\"exp\":1800043200")
                .contains("\"sub\":\"mailto:shop@example.com\"");
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initVerify(k.getPublic());
        s.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(s.verify(WebPushCrypto.unb64(parts[2]))).isTrue();
    }
}
