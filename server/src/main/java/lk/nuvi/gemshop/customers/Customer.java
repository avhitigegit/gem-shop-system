package lk.nuvi.gemshop.customers;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lk.nuvi.gemshop.common.crypto.EncryptedString;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/**
 * A buyer (BRD F5, J2): name and country are required for the invoice; email and WhatsApp to send it. The birthday is
 * day + month only ("MM-DD"). Promotions only with the customer's permission (PDPA). The passport number is encrypted.
 */
@Entity
@Table(name = "customer")
public class Customer extends BaseEntity {

    /** What the salesperson types on the new-customer form. */
    public record Details(String name, String country, String email, String whatsapp, String birthday, boolean consent) {}

    @Column(nullable = false, length = 12, updatable = false)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 60)
    private String country;

    @Column(length = 160)
    private String email;

    @Column(length = 40)
    private String whatsapp;

    @Column(length = 5)
    private String birthday;

    @Column(nullable = false)
    private boolean consent;

    private Instant consentAt;

    @Column(nullable = false)
    private boolean unsubscribed;

    @Convert(converter = EncryptedString.class)
    @Column(columnDefinition = "text")
    private String passport;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private Instant joinedAt;

    protected Customer() {}

    public Customer(String code, Details d, Instant joinedAt) {
        this.code = code;
        this.name = d.name();
        this.country = d.country();
        this.email = d.email();
        this.whatsapp = d.whatsapp();
        this.birthday = d.birthday();
        this.consent = d.consent();
        this.consentAt = d.consent() ? joinedAt : null;
        this.joinedAt = joinedAt;
    }

    /** Sample data only: the prototype's extra fields. */
    public Customer extra(Instant consentAt, boolean unsubscribed, String passport, String notes) {
        this.consentAt = consent ? consentAt : null;
        this.unsubscribed = unsubscribed;
        this.passport = passport;
        this.notes = notes;
        return this;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public String getEmail() { return email; }
    public String getWhatsapp() { return whatsapp; }
    public String getBirthday() { return birthday; }
    public boolean isConsent() { return consent; }
    public Instant getConsentAt() { return consentAt; }
    public boolean isUnsubscribed() { return unsubscribed; }
    public String getPassport() { return passport; }
    public String getNotes() { return notes; }
    public Instant getJoinedAt() { return joinedAt; }
}
