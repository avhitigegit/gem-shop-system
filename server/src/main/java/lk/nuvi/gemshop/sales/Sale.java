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
import java.util.ArrayList;
import java.util.List;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.commissions.Guide;
import lk.nuvi.gemshop.common.money.Currency;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.customers.Customer;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.settings.branch.Branch;

/**
 * One sale = one invoice (BRD F6, G1). Prices are in LKR and the same for cash or card; the 3% card fee is a shop cost
 * ({@link #getCardFee()}), never added to the customer's price. Commissions are worked out when the sale is made (H3).
 */
@Entity
@Table(name = "sale")
public class Sale extends BaseEntity {

    public enum Status { COMPLETED, CANCELLED }

    /** Totals, commissions and payment results of a sale. */
    public record Money(BigDecimal total, BigDecimal guidePct, BigDecimal guideComm, BigDecimal staffComm,
            BigDecimal cardFee, BigDecimal change, BigDecimal rateUsd, BigDecimal rateEur) {}

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @Column(nullable = false)
    private Instant soldAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guide_id")
    private Guide guide;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private UserAccount staff;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.COMPLETED;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal guidePct;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal guideComm;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal staffComm;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cardFee;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal changeDue;

    @Column(precision = 12, scale = 4)
    private BigDecimal rateUsd;

    @Column(precision = 12, scale = 4)
    private BigDecimal rateEur;

    @Column(length = 20)
    private String heldNo;

    @Column(length = 20)
    private String quoteNo;

    @Column(nullable = false)
    private boolean sentEmail;

    @Column(nullable = false)
    private boolean sentWhatsapp;

    @Column(length = 500)
    private String notes;

    @Column(length = 20)
    private String cancelApprovalNo;

    @Column(length = 500)
    private String cancelReason;

    private Instant cancelledAt;

    @Column(length = 120)
    private String cancelledBy;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<SaleItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<SalePayment> payments = new ArrayList<>();

    protected Sale() {}

    public Sale(String number, Instant soldAt, Branch branch, Customer customer, Guide guide, UserAccount staff, Money m,
            String heldNo, String notes) {
        this.number = number;
        this.soldAt = soldAt;
        this.branch = branch;
        this.customer = customer;
        this.guide = guide;
        this.staff = staff;
        this.total = m.total();
        this.guidePct = m.guidePct();
        this.guideComm = m.guideComm();
        this.staffComm = m.staffComm();
        this.cardFee = m.cardFee();
        this.changeDue = m.change();
        this.rateUsd = m.rateUsd();
        this.rateEur = m.rateEur();
        this.heldNo = heldNo;
        this.notes = notes;
    }

    public void add(Gem gem, BigDecimal listPrice, BigDecimal agreed, BigDecimal minPrice, String approvalNo) {
        items.add(new SaleItem(this, items.size() + 1, gem, listPrice, agreed, minPrice, approvalNo));
    }

    public void pay(SalePayment.Method method, Currency currency, BigDecimal amount, BigDecimal rate, BigDecimal lkr,
            BigDecimal feePct, BigDecimal fee) {
        payments.add(new SalePayment(this, payments.size() + 1, method, currency, amount, rate, lkr, feePct, fee));
    }

    public void sent(boolean email, boolean whatsapp) {
        this.sentEmail = sentEmail || email;
        this.sentWhatsapp = sentWhatsapp || whatsapp;
    }

    /** Cancellation asked (rule 7): the owner decides request {@code approvalNo}. */
    public void cancelAsked(String approvalNo, String reason) {
        this.cancelApprovalNo = approvalNo;
        this.cancelReason = reason;
    }

    /** The owner approved the cancellation: the invoice stays on record, marked CANCELLED (rule 4). */
    public void cancel(Instant at, String by) {
        this.status = Status.CANCELLED;
        this.cancelledAt = at;
        this.cancelledBy = by;
    }

    public String getNumber() { return number; }
    public Instant getSoldAt() { return soldAt; }
    public Branch getBranch() { return branch; }
    public Customer getCustomer() { return customer; }
    public Guide getGuide() { return guide; }
    public UserAccount getStaff() { return staff; }
    public Status getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getGuidePct() { return guidePct; }
    public BigDecimal getGuideComm() { return guideComm; }
    public BigDecimal getStaffComm() { return staffComm; }
    public BigDecimal getCardFee() { return cardFee; }
    public BigDecimal getChangeDue() { return changeDue; }
    public BigDecimal getRateUsd() { return rateUsd; }
    public BigDecimal getRateEur() { return rateEur; }
    public String getHeldNo() { return heldNo; }
    public String getQuoteNo() { return quoteNo; }
    public boolean isSentEmail() { return sentEmail; }
    public boolean isSentWhatsapp() { return sentWhatsapp; }
    public String getNotes() { return notes; }
    public String getCancelApprovalNo() { return cancelApprovalNo; }
    public String getCancelReason() { return cancelReason; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancelledBy() { return cancelledBy; }
    public List<SaleItem> getItems() { return items; }
    public List<SalePayment> getPayments() { return payments; }
}
