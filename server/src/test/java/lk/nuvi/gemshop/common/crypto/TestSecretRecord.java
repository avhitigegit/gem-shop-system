package lk.nuvi.gemshop.common.crypto;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/** Test only: how a customer's passport, phone and email are stored (P4-03 does the same). */
@Entity
@Table(name = "test_secret_record")
public class TestSecretRecord extends BaseEntity {

    @Convert(converter = EncryptedString.class)
    private String passport;

    @Convert(converter = EncryptedString.class)
    private String phone;

    @Convert(converter = EncryptedString.class)
    private String email;

    @Column(length = 64)
    private String emailIndex;

    protected TestSecretRecord() {}

    public TestSecretRecord(String passport, String phone, String email, String emailIndex) {
        this.passport = passport;
        this.phone = phone;
        this.email = email;
        this.emailIndex = emailIndex;
    }

    public String getPassport() { return passport; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getEmailIndex() { return emailIndex; }
}
