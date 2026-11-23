package lk.nuvi.gemshop.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.settings.branch.Branch;

/**
 * A customer brings a gem back (RT-…, BRD E1 / E2). Refund = money back with a credit note; exchange = the credit note
 * pays for another gem. Always needs the owner's approval (rule 7).
 */
@Entity
@Table(name = "sale_return")
public class SaleReturn extends BaseEntity {

    public enum Kind { REFUND, EXCHANGE }

    public enum Status { PENDING, APPROVED, REJECTED }

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @Column(nullable = false, updatable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_item_id")
    private SaleItem item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gem_id")
    private Gem gem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Kind kind;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private UserAccount staff;

    @Column(length = 20)
    private String approvalNo;

    @OneToOne(mappedBy = "saleReturn", fetch = FetchType.LAZY)
    private CreditNote creditNote;

    protected SaleReturn() {}

    public SaleReturn(String number, Instant requestedAt, Sale sale, SaleItem item, Kind kind, String reason, UserAccount staff) {
        this.number = number;
        this.requestedAt = requestedAt;
        this.branch = sale.getBranch();
        this.sale = sale;
        this.item = item;
        this.gem = item.getGem();
        this.kind = kind;
        this.reason = reason;
        this.amount = item.getAgreed();
        this.staff = staff;
    }

    public void asked(String approvalNo) {
        this.approvalNo = approvalNo;
    }

    public void decided(boolean approved) {
        this.status = approved ? Status.APPROVED : Status.REJECTED;
    }

    void issued(CreditNote cn) {
        this.creditNote = cn;
    }

    public boolean isPending() { return status == Status.PENDING; }
    public String getNumber() { return number; }
    public Instant getRequestedAt() { return requestedAt; }
    public Branch getBranch() { return branch; }
    public Sale getSale() { return sale; }
    public SaleItem getItem() { return item; }
    public Gem getGem() { return gem; }
    public Kind getKind() { return kind; }
    public String getReason() { return reason; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public UserAccount getStaff() { return staff; }
    public String getApprovalNo() { return approvalNo; }
    public CreditNote getCreditNote() { return creditNote; }
}
