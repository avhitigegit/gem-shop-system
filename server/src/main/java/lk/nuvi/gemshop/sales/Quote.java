package lk.nuvi.gemshop.sales;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.customers.Customer;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.settings.branch.Branch;

/**
 * Keep a gem for a customer (reservation, RS-…) or give a price quote (quotation, QT-…) – BRD F7, rule 3. Both have an
 * expiry date. A reservation makes its gems "Reserved": nobody else can buy them until it is released, expires or is
 * sold. A quotation only promises the price; the gems stay on sale.
 */
@Entity
@Table(name = "quote")
public class Quote extends BaseEntity {

    public enum Kind { RESERVATION, QUOTATION }

    public enum Status { ACTIVE, EXPIRED, RELEASED, CONVERTED }

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12, updatable = false)
    private Kind kind;

    @Column(nullable = false)
    private Instant createdOn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private UserAccount staff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private LocalDate expires;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal deposit = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.ACTIVE;

    @Column(length = 20)
    private String saleNo;

    @Column(nullable = false)
    private boolean sentEmail;

    @Column(nullable = false)
    private boolean sentWhatsapp;

    @Column(length = 500)
    private String notes;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<QuoteItem> items = new ArrayList<>();

    protected Quote() {}

    public Quote(String number, Kind kind, Instant createdOn, Branch branch, UserAccount staff, Customer customer,
            LocalDate expires, BigDecimal deposit, String notes) {
        this.number = number;
        this.kind = kind;
        this.createdOn = createdOn;
        this.branch = branch;
        this.staff = staff;
        this.customer = customer;
        this.expires = expires;
        this.deposit = deposit == null ? BigDecimal.ZERO : deposit;
        this.notes = notes;
    }

    public void add(Gem gem, BigDecimal price) {
        items.add(new QuoteItem(this, items.size() + 1, gem, price));
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isReservation() {
        return kind == Kind.RESERVATION;
    }

    public void extend(LocalDate to) {
        this.expires = to;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    /** Sold on invoice {@code saleNo}. */
    public void converted(String saleNo) {
        this.status = Status.CONVERTED;
        this.saleNo = saleNo;
    }

    public void sent(boolean email, boolean whatsapp) {
        this.sentEmail = sentEmail || email;
        this.sentWhatsapp = sentWhatsapp || whatsapp;
    }

    public String getNumber() { return number; }
    public Kind getKind() { return kind; }
    public Instant getCreatedOn() { return createdOn; }
    public Branch getBranch() { return branch; }
    public UserAccount getStaff() { return staff; }
    public Customer getCustomer() { return customer; }
    public LocalDate getExpires() { return expires; }
    public BigDecimal getDeposit() { return deposit; }
    public Status getStatus() { return status; }
    public String getSaleNo() { return saleNo; }
    public boolean isSentEmail() { return sentEmail; }
    public boolean isSentWhatsapp() { return sentWhatsapp; }
    public String getNotes() { return notes; }
    public List<QuoteItem> getItems() { return items; }

    public BigDecimal total() {
        return items.stream().map(QuoteItem::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
