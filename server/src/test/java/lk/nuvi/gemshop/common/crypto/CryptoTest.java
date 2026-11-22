package lk.nuvi.gemshop.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import lk.nuvi.gemshop.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** P1-05: passport, phone and email are stored encrypted; passport shown masked. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CryptoTest {

    @Autowired FieldCrypto crypto;
    @Autowired TestSecretRecordRepository records;
    @Autowired JdbcTemplate jdbc;

    @Test
    void theDatabaseOnlyHoldsEncryptedValues() {
        String email = "anna.mueller@web.de";
        TestSecretRecord saved = records.save(new TestSecretRecord("GE4000000", "+49 151 2345 6789", email, crypto.emailIndex(email)));
        var raw = jdbc.queryForMap("select passport, phone, email from test_secret_record where id = ?", saved.getId());
        for (Object v : raw.values()) {
            assertThat((String) v).startsWith("v1:").doesNotContain("GE4000000").doesNotContain("2345").doesNotContain("mueller");
        }
        TestSecretRecord read = records.findById(saved.getId()).orElseThrow();
        assertThat(read.getPassport()).isEqualTo("GE4000000");
        assertThat(read.getPhone()).isEqualTo("+49 151 2345 6789");
        assertThat(read.getEmail()).isEqualTo(email);
        // found by email without decrypting every row – whatever the capitals and spaces
        assertThat(records.findByEmailIndex(crypto.emailIndex("  Anna.Mueller@WEB.de "))).isPresent();
    }

    @Test
    void sameTextGivesADifferentValueEachTime() {
        String a = crypto.encrypt("GE4000000");
        String b = crypto.encrypt("GE4000000");
        assertThat(a).isNotEqualTo(b);
        assertThat(crypto.decrypt(a)).isEqualTo("GE4000000");
        assertThat(crypto.decrypt(b)).isEqualTo("GE4000000");
        assertThat(crypto.encrypt("Müller – 日本")).satisfies(x -> assertThat(crypto.decrypt(x)).isEqualTo("Müller – 日本"));
    }

    @Test
    void anotherKeyCannotRead() {
        FieldCrypto other = new FieldCrypto(props("another-key-that-is-long-enough-0123456789", "local"));
        String secret = crypto.encrypt("GE4000000");
        assertThatThrownBy(() -> other.decrypt(secret)).hasMessageContaining("ENCRYPTION_KEY");
        assertThatThrownBy(() -> crypto.decrypt(secret.substring(0, secret.length() - 4) + "AAAA"))
                .isInstanceOf(IllegalStateException.class); // changed in the database → refused
    }

    @Test
    void keyRules() {
        assertThatThrownBy(() -> new FieldCrypto(props("short", "local"))).hasMessageContaining("at least 32");
        assertThatThrownBy(() -> new FieldCrypto(props(null, "local"))).hasMessageContaining("at least 32");
        assertThatThrownBy(() -> new FieldCrypto(props("local-only-encryption-key-change-me-0123456789", "prod")))
                .hasMessageContaining("real ENCRYPTION_KEY");
        assertThatThrownBy(() -> new FieldCrypto(props("local-only-encryption-key-change-me-0123456789", "uat")))
                .hasMessageContaining("real ENCRYPTION_KEY");
        // creating test copies must not replace the app's key
        new FieldCrypto(props("yet-another-key-that-is-long-enough-012345", "local"));
    }

    @Test
    void searchKeysIgnoreFormatting() {
        assertThat(crypto.phoneIndex("+94 77 123 4567")).isEqualTo(crypto.phoneIndex("+94-77-1234567"));
        assertThat(crypto.passportIndex("ge 400 0000")).isEqualTo(crypto.passportIndex("GE4000000"));
        assertThat(crypto.emailIndex("")).isNull();
        assertThat(crypto.phoneIndex("—")).isNull();
        assertThat(crypto.emailIndex("a@b.lk")).hasSize(64).isNotEqualTo(crypto.emailIndex("b@b.lk"));
    }

    @Test
    void passportIsShownMasked() {
        assertThat(Masks.passport("GE4000000")).isEqualTo("•••• 000");
        assertThat(Masks.passport("N 1234 567")).isEqualTo("•••• 567");
        assertThat(Masks.passport("AB")).isEqualTo("••••");
        assertThat(Masks.passport("")).isNull();
        assertThat(Masks.passport(null)).isNull();
    }

    private static AppProperties props(String key, String env) {
        return new AppProperties("Gem Shop System", env, "Asia/Colombo", new AppProperties.Cors(List.of()),
                new AppProperties.SampleData(false, null), new AppProperties.Crypto(key),
                new AppProperties.Files("target/test-files", 60, 24));
    }
}
