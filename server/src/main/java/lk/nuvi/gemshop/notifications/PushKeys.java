package lk.nuvi.gemshop.notifications;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import lk.nuvi.gemshop.common.crypto.FieldCrypto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The shop's VAPID key pair (RFC 8292). Made once, on first use, and kept in the database – the private key encrypted with
 * ENCRYPTION_KEY. Phones signed up with this public key; a new key would need every phone to sign up again.
 */
@Component
public class PushKeys {

    public record Vapid(String publicKey, PrivateKey privateKey) {}

    private final JdbcTemplate jdbc;
    private final FieldCrypto crypto;
    private volatile Vapid cached;

    public PushKeys(JdbcTemplate jdbc, FieldCrypto crypto) {
        this.jdbc = jdbc;
        this.crypto = crypto;
    }

    public Vapid get() {
        Vapid v = cached;
        if (v == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = load();
                }
                v = cached;
            }
        }
        return v;
    }

    private Vapid load() {
        if (jdbc.queryForObject("select count(*) from push_key", Integer.class) == 0) {
            KeyPair k = WebPushCrypto.newKeyPair();
            jdbc.update("insert into push_key (id, public_key, private_key, created_at) values (1, ?, ?, ?) on conflict (id) do nothing",
                    WebPushCrypto.b64(WebPushCrypto.raw((ECPublicKey) k.getPublic())),
                    crypto.encrypt(WebPushCrypto.b64(k.getPrivate().getEncoded())), Timestamp.from(Instant.now()));
        }
        Map<String, Object> row = jdbc.queryForMap("select public_key, private_key from push_key where id = 1");
        try {
            return new Vapid((String) row.get("public_key"),
                    WebPushCrypto.privateKey(WebPushCrypto.unb64(crypto.decrypt((String) row.get("private_key")))));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Push key cannot be read – was ENCRYPTION_KEY changed?", e);
        }
    }
}
