package lk.nuvi.gemshop.audit.approval;

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
import java.util.Objects;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.settings.branch.Branch;

/**
 * A request that waits for the owner (K3). The owner approves or rejects it with an optional note;
 * the module that asked then finishes its action ({@link ApprovalHandler}).
 */
@Entity
@Table(name = "approval")
public class Approval extends BaseEntity {

    /** AP-0001 */
    @Column(nullable = false, updatable = false, length = 20)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private ApprovalType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false, updatable = false)
    private UserAccount requestedBy;

    /** Empty = whole shop (re-open a closed month). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", updatable = false)
    private Branch branch;

    @Column(nullable = false, updatable = false, length = 200)
    private String title;

    @Column(nullable = false, updatable = false, length = 1000)
    private String detail;

    /** In LKR (e.g. the agreed price, the refund, the expense). Empty when there is no amount. */
    @Column(name = "amount_lkr", updatable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** The waiting record, e.g. held sale HS-0001, return RT-0002, invoice INV-000140, month 2026-09. */
    @Column(updatable = false, length = 40)
    private String ref;

    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private UserAccount decidedBy;

    @Column(nullable = false, length = 500)
    private String note = "";

    protected Approval() {}

    public Approval(String number, ApprovalType type, Instant requestedAt, UserAccount requestedBy, Branch branch,
            String title, String detail, BigDecimal amount, String ref) {
        this.number = Objects.requireNonNull(number);
        this.type = Objects.requireNonNull(type);
        this.requestedAt = Objects.requireNonNull(requestedAt);
        this.requestedBy = Objects.requireNonNull(requestedBy);
        this.branch = branch;
        this.title = Objects.requireNonNull(title);
        this.detail = detail == null ? "" : detail;
        this.amount = amount;
        this.ref = ref;
    }

    /** Approve or reject. Only once: a decided request cannot be changed. */
    public void decide(boolean approve, UserAccount by, String note, Instant at) {
        if (status != ApprovalStatus.PENDING) {
            throw new IllegalStateException(number + " is already " + status);
        }
        this.status = approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
        this.decidedBy = by;
        this.decidedAt = at;
        this.note = note == null ? "" : note.trim();
    }

    public boolean isPending() { return status == ApprovalStatus.PENDING; }
    public String getNumber() { return number; }
    public ApprovalType getType() { return type; }
    public ApprovalStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public UserAccount getRequestedBy() { return requestedBy; }
    public Branch getBranch() { return branch; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public BigDecimal getAmount() { return amount; }
    public String getRef() { return ref; }
    public Instant getDecidedAt() { return decidedAt; }
    public UserAccount getDecidedBy() { return decidedBy; }
    public String getNote() { return note; }
}
