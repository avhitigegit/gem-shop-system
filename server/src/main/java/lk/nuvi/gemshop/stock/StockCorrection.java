package lk.nuvi.gemshop.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.gems.Gem;

/**
 * A fix of a stock mistake (BRD D7): the status (lost / damaged / found), the branch (wrong place) or the carat weight
 * (data error). Nothing changes on the gem until the owner approves.
 */
@Entity
@Table(name = "stock_correction")
public class StockCorrection extends BaseEntity {

    public enum Kind { STATUS, BRANCH, CARAT }

    public enum Status { PENDING, APPROVED, REJECTED }

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gem_id")
    private Gem gem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(name = "from_value", nullable = false, length = 40)
    private String from;

    @Column(name = "to_value", nullable = false, length = 40)
    private String to;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private UserAccount requestedBy;

    @Column(length = 20)
    private String approvalNo;

    protected StockCorrection() {}

    public StockCorrection(String number, Gem gem, Kind kind, String from, String to, String reason, Instant requestedAt,
            UserAccount requestedBy) {
        this.number = number;
        this.gem = gem;
        this.kind = kind;
        this.from = from;
        this.to = to;
        this.reason = reason;
        this.requestedAt = requestedAt;
        this.requestedBy = requestedBy;
    }

    public void askedIn(String approvalNo) {
        this.approvalNo = approvalNo;
    }

    public void decided(boolean approved) {
        this.status = approved ? Status.APPROVED : Status.REJECTED;
    }

    public String getNumber() { return number; }
    public Gem getGem() { return gem; }
    public Kind getKind() { return kind; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getReason() { return reason; }
    public Status getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public UserAccount getRequestedBy() { return requestedBy; }
    public String getApprovalNo() { return approvalNo; }
}
