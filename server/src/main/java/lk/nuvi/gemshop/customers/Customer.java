package lk.nuvi.gemshop.customers;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import lk.nuvi.gemshop.common.crypto.EncryptedString;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/**
 * A buyer (BRD F5, J2): name and country are required for the invoice; email and WhatsApp to send it. The birthday is
 * day + month only ("MM-DD"). Promotions only with the customer's permission (PDPA) and not after they unsubscribed (link in
 * the offer, or STOP on WhatsApp). The passport number is encrypted.
 */
@Entity
@Table(name = "customer")
public class Customer extends BaseEntity {

    /** What the salesperson types on the new-customer form. */
    public record Details(String name, String country, String email, String whatsapp, String birthday, boolean consent) {}

    /** How the customer unsubscribed. */
    public enum UnsubVia { LINK, WHATSAPP }

    private static final SecureRandom RANDOM = new SecureRandom();

    @Column(nullable = false, length = 12, updatable = false)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 60)
    private String country;

    /** Encrypted in the database (PDPA); checked to 160 characters by {@link Customers#check}. */
    @Convert(converter = EncryptedString.class)
    @Column(columnDefinition = "text")
    private String email;

    /** Encrypted in the database (PDPA); checked to 40 characters by {@link Customers#check}. */
    @Convert(converter = EncryptedString.class)
    @Column(columnDefinition = "text")
    private String whatsapp;

    @Column(length = 5)
    private String birthday;

    @Column(nullable = false)
    private boolean consent;

    private Instant consentAt;

    @Column(nullable = false)
    private boolean unsubscribed;

    private Instant unsubscribedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private UnsubVia unsubVia;

    /** Private token of the unsubscribe link (random, 43 characters). */
    @Column(nullable = false, length = 64, unique = true)
    private String unsubToken;

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
        this.unsubToken = newToken();
    }

    /** Edit: name, country, email, WhatsApp, birthday (permission has its own switch). */
    public void change(Details d) {
        this.name = d.name();
        this.country = d.country();
        this.email = d.email();
        this.whatsapp = d.whatsapp();
        this.birthday = d.birthday();
    }

    public void passport(String passport) {
        this.passport = passport;
    }

    /**
     * The permission switch at the counter. On = permission given now (also again after an unsubscribe – the customer asked
     * in person). Off = permission withdrawn; the date it was given stays on record.
     */
    public void consent(boolean on, Instant at) {
        if (on) {
            this.consent = true;
            this.consentAt = at;
            this.unsubscribed = false;
            this.unsubscribedAt = null;
            this.unsubVia = null;
        } else {
            this.consent = false;
        }
    }

    /** Unsubscribe link or WhatsApp STOP. False when the customer was already unsubscribed. */
    public boolean unsubscribe(Instant at, UnsubVia via) {
        if (unsubscribed) {
            return false;
        }
        this.unsubscribed = true;
        this.unsubscribedAt = at;
        this.unsubVia = via;
        return true;
    }

    /** May get offers and birthday wishes (rule 8). */
    public boolean canMessage() {
        return consent && !unsubscribed;
    }

    private static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Sample data only: the prototype's extra fields. */
    public Customer extra(Instant consentAt, boolean unsubscribed, String passport, String notes) {
        this.consentAt = consent ? consentAt : null;
        this.unsubscribed = unsubscribed;
        this.unsubscribedAt = unsubscribed ? joinedAt : null;
        this.unsubVia = unsubscribed ? UnsubVia.LINK : null;
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
    public Instant getUnsubscribedAt() { return unsubscribedAt; }
    public UnsubVia getUnsubVia() { return unsubVia; }
    public String getUnsubToken() { return unsubToken; }
    public String getPassport() { return passport; }
    public String getNotes() { return notes; }
    public Instant getJoinedAt() { return joinedAt; }
}
