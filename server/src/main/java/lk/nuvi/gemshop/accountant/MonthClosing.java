package lk.nuvi.gemshop.accountant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.YearMonth;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/** A month that was closed (BRD N6, rule 11). Re-opened by the owner = status OPEN with the reason; it can be closed again. */
@Entity
@Table(name = "month_closing")
public class MonthClosing extends BaseEntity {

    public enum Status { CLOSED, OPEN }

    @Column(nullable = false, length = 7, updatable = false)
    private String month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Status status;

    @Column(nullable = false)
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_id")
    private UserAccount closedBy;

    private Instant reopenedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reopened_by_id")
    private UserAccount reopenedBy;

    @Column(length = 500)
    private String reopenReason;

    protected MonthClosing() {}

    public MonthClosing(YearMonth month, Instant at, UserAccount by) {
        this.month = month.toString();
        close(at, by);
    }

    public void close(Instant at, UserAccount by) {
        this.status = Status.CLOSED;
        this.closedAt = at;
        this.closedBy = by;
    }

    public void reopen(Instant at, UserAccount by, String reason) {
        this.status = Status.OPEN;
        this.reopenedAt = at;
        this.reopenedBy = by;
        this.reopenReason = reason;
    }

    public boolean isClosed() { return status == Status.CLOSED; }
    public YearMonth getMonth() { return YearMonth.parse(month); }
    public Status getStatus() { return status; }
    public Instant getClosedAt() { return closedAt; }
    public UserAccount getClosedBy() { return closedBy; }
    public Instant getReopenedAt() { return reopenedAt; }
    public UserAccount getReopenedBy() { return reopenedBy; }
    public String getReopenReason() { return reopenReason; }
}
