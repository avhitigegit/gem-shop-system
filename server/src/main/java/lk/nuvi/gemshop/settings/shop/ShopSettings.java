package lk.nuvi.gemshop.settings.shop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/**
 * Shop details (Settings → Shop details, BRD A3). One row only.
 * No VAT number and no VAT rate – prices already include VAT and VAT is never shown.
 * The card bank fee % is a cost the shop pays; the customer pays the same price by cash or card.
 */
@Entity
@Table(name = "shop_settings")
public class ShopSettings extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 160)
    private String tagline;

    @Column(length = 255)
    private String address;

    @Column(length = 40)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(length = 120)
    private String website;

    /** Stored file name of the logo (P1-05 file storage). */
    @Column(length = 255)
    private String logoFile;

    @Column(length = 60)
    private String ngjaLicenceNo;

    private LocalDate ngjaLicenceExpiry;

    /** Remind the owner this many days before the licence expires. */
    @Column(nullable = false)
    private int ngjaRemindDays = 60;

    @Column(nullable = false)
    private boolean printLicenceOnInvoice = true;

    /** Expenses above this amount (LKR) need the owner's approval (M6). */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal largeExpenseLimit = new BigDecimal("50000.00");

    /** Bank fee on card payments, paid by the shop (BRD rule 12). */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal cardFeePct = new BigDecimal("3.00");

    @Column(nullable = false, length = 10)
    private String invoicePrefix = "INV-";

    @Column(nullable = false)
    private int returnPolicyDays = 14;

    protected ShopSettings() {}

    public ShopSettings(String name) {
        this.name = name;
    }

    public ShopSettings contact(String tagline, String address, String phone, String email, String website) {
        this.tagline = tagline;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.website = website;
        return this;
    }

    public ShopSettings licence(String no, LocalDate expiry, int remindDays, boolean printOnInvoice) {
        this.ngjaLicenceNo = no;
        this.ngjaLicenceExpiry = expiry;
        this.ngjaRemindDays = remindDays;
        this.printLicenceOnInvoice = printOnInvoice;
        return this;
    }

    public ShopSettings rules(BigDecimal largeExpenseLimit, BigDecimal cardFeePct, String invoicePrefix, int returnPolicyDays) {
        this.largeExpenseLimit = largeExpenseLimit;
        this.cardFeePct = cardFeePct;
        this.invoicePrefix = invoicePrefix;
        this.returnPolicyDays = returnPolicyDays;
        return this;
    }

    public String getName() { return name; }
    public String getTagline() { return tagline; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getWebsite() { return website; }
    public String getLogoFile() { return logoFile; }
    public String getNgjaLicenceNo() { return ngjaLicenceNo; }
    public LocalDate getNgjaLicenceExpiry() { return ngjaLicenceExpiry; }
    public int getNgjaRemindDays() { return ngjaRemindDays; }
    public boolean isPrintLicenceOnInvoice() { return printLicenceOnInvoice; }
    public BigDecimal getLargeExpenseLimit() { return largeExpenseLimit; }
    public BigDecimal getCardFeePct() { return cardFeePct; }
    public String getInvoicePrefix() { return invoicePrefix; }
    public int getReturnPolicyDays() { return returnPolicyDays; }

    public void setLogoFile(String logoFile) {
        this.logoFile = logoFile;
    }
}
