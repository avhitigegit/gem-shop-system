package lk.nuvi.gemshop.commissions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.settings.branch.Branch;

/**
 * What one person earned on one sale (BRD H1–H3): the guide / driver's % or the salesperson's level amount. A return or
 * a cancelled invoice adds a negative line (status REVERSED). A line is still owed while {@code paidAt} is empty – a
 * negative line is then taken off the person's next payment.
 */
@Entity
@Table(name = "commission_line")
public class CommissionLine extends BaseEntity {

    public enum Payee { GUIDE, STAFF }

    public enum Status { UNPAID, PAID, REVERSED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", updatable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", updatable = false)
    private Branch branch;

    @Column(nullable = false, updatable = false)
    private Instant earnedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5, updatable = false)
    private Payee payee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guide_id", updatable = false)
    private Guide guide;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", updatable = false)
    private UserAccount staff;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(precision = 5, scale = 2, updatable = false)
    private BigDecimal pct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Status status;

    @Column(length = 200)
    private String note;

    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private PayMethod method;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private CommissionPayment payment;

    protected CommissionLine() {}

    private CommissionLine(Sale sale, Instant earnedAt, Payee payee, Guide guide, UserAccount staff, BigDecimal amount, BigDecimal pct,
            Status status, String note) {
        this.sale = sale;
        this.branch = sale.getBranch();
        this.earnedAt = earnedAt;
        this.payee = payee;
        this.guide = guide;
        this.staff = staff;
        this.amount = amount;
        this.pct = pct;
        this.status = status;
        this.note = note;
    }

    public static CommissionLine guide(Sale s, Guide g, BigDecimal amount, BigDecimal pct) {
        return new CommissionLine(s, s.getSoldAt(), Payee.GUIDE, g, null, amount, pct, Status.UNPAID, null);
    }

    public static CommissionLine staff(Sale s, UserAccount u, BigDecimal amount) {
        return new CommissionLine(s, s.getSoldAt(), Payee.STAFF, null, u, amount, null, Status.UNPAID, null);
    }

    /** A negative line: commission taken back after a return or a cancelled invoice. */
    public static CommissionLine takenBack(CommissionLine like, Instant at, BigDecimal amount, String note) {
        return new CommissionLine(like.sale, at, like.payee, like.guide, like.staff, amount.negate(), like.pct, Status.REVERSED, note);
    }

    /** Paid with a payment (a negative line is settled with it). */
    public void paid(CommissionPayment p) {
        settled(p.getPaidAt(), p.getMethod());
        this.payment = p;
    }

    /** Sample data: paid before this system (no payment record). */
    public void settled(Instant at, PayMethod m) {
        if (status == Status.UNPAID) {
            status = Status.PAID;
        }
        this.paidAt = at;
        this.method = m;
    }

    public boolean isOwed() {
        return paidAt == null;
    }

    /** "G:G-01" or "S:12" – one person. */
    public String personKey() {
        return payee == Payee.GUIDE ? "G:" + guide.getCode() : "S:" + staff.getId();
    }

    public String personName() {
        return payee == Payee.GUIDE ? guide.getName() : staff.getName();
    }

    public Sale getSale() { return sale; }
    public Branch getBranch() { return branch; }
    public Instant getEarnedAt() { return earnedAt; }
    public Payee getPayee() { return payee; }
    public Guide getGuide() { return guide; }
    public UserAccount getStaff() { return staff; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getPct() { return pct; }
    public Status getStatus() { return status; }
    public String getNote() { return note; }
    public Instant getPaidAt() { return paidAt; }
    public PayMethod getMethod() { return method; }
    public CommissionPayment getPayment() { return payment; }
}
