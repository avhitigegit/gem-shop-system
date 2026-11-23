package lk.nuvi.gemshop.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/**
 * Credit note (CN-…, BRD E2): made when the owner approves a return. The amount is the price paid for the gem – a card
 * fee is not given back (it stays a shop cost). Records the commissions taken back. An exchange credit note pays for
 * the new gem once. Never deleted (rule 4).
 */
@Entity
@Table(name = "credit_note")
public class CreditNote extends BaseEntity {

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @Column(nullable = false, updatable = false)
    private Instant issuedAt;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id")
    private SaleReturn saleReturn;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal guideComm = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal staffComm = BigDecimal.ZERO;

    @Column(nullable = false, length = 120)
    private String approvedBy;

    @Column(length = 20)
    private String usedSaleNo;

    @Column(precision = 14, scale = 2)
    private BigDecimal usedAmount;

    @Column(nullable = false)
    private boolean sentEmail;

    @Column(nullable = false)
    private boolean sentWhatsapp;

    /** A refund paid back in cash from the drawer (P3-06). */
    private Instant cashRefundAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_refund_branch_id")
    private lk.nuvi.gemshop.settings.branch.Branch cashRefundBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_refund_by_id")
    private lk.nuvi.gemshop.auth.user.UserAccount cashRefundBy;

    protected CreditNote() {}

    public CreditNote(String number, Instant issuedAt, SaleReturn r, BigDecimal guideComm, BigDecimal staffComm, String approvedBy) {
        this.number = number;
        this.issuedAt = issuedAt;
        this.saleReturn = r;
        this.amount = r.getAmount();
        this.guideComm = guideComm;
        this.staffComm = staffComm;
        this.approvedBy = approvedBy;
        r.issued(this);
    }

    /** An exchange: paid {@code amount} of the new sale. */
    public void used(String saleNo, BigDecimal amount) {
        this.usedSaleNo = saleNo;
        this.usedAmount = amount;
    }

    public void sent(boolean email, boolean whatsapp) {
        this.sentEmail |= email;
        this.sentWhatsapp |= whatsapp;
    }

    public boolean isUsed() { return usedSaleNo != null; }

    /** Paid back in cash from the drawer of {@code branch}. */
    public void refundedFromDrawer(Instant at, lk.nuvi.gemshop.settings.branch.Branch branch, lk.nuvi.gemshop.auth.user.UserAccount by) {
        this.cashRefundAt = at;
        this.cashRefundBranch = branch;
        this.cashRefundBy = by;
    }

    public boolean isRefundedFromDrawer() { return cashRefundAt != null; }
    public Instant getCashRefundAt() { return cashRefundAt; }
    public lk.nuvi.gemshop.settings.branch.Branch getCashRefundBranch() { return cashRefundBranch; }
    public lk.nuvi.gemshop.auth.user.UserAccount getCashRefundBy() { return cashRefundBy; }
    public String getNumber() { return number; }
    public Instant getIssuedAt() { return issuedAt; }
    public SaleReturn getSaleReturn() { return saleReturn; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getGuideComm() { return guideComm; }
    public BigDecimal getStaffComm() { return staffComm; }
    public String getApprovedBy() { return approvedBy; }
    public String getUsedSaleNo() { return usedSaleNo; }
    public BigDecimal getUsedAmount() { return usedAmount; }
    public boolean isSentEmail() { return sentEmail; }
    public boolean isSentWhatsapp() { return sentWhatsapp; }
}
