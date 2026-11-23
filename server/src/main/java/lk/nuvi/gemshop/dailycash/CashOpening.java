package lk.nuvi.gemshop.dailycash;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.settings.branch.Branch;

/** Cash in the drawer at the start of the day, per currency (BRD I1). One per branch per day; changeable until closed. */
@Entity
@Table(name = "cash_opening")
public class CashOpening extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", updatable = false)
    private Branch branch;

    @Column(nullable = false, updatable = false)
    private LocalDate day;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lkr;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal usd;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal eur;

    @Column(nullable = false)
    private Instant enteredAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entered_by_id")
    private UserAccount enteredBy;

    protected CashOpening() {}

    public CashOpening(Branch branch, LocalDate day) {
        this.branch = branch;
        this.day = day;
    }

    public void set(Money3 m, Instant at, UserAccount by) {
        this.lkr = m.lkr();
        this.usd = m.usd();
        this.eur = m.eur();
        this.enteredAt = at;
        this.enteredBy = by;
    }

    public Money3 money() {
        return new Money3(lkr, usd, eur);
    }

    public Branch getBranch() { return branch; }
    public LocalDate getDay() { return day; }
    public Instant getEnteredAt() { return enteredAt; }
    public UserAccount getEnteredBy() { return enteredBy; }
}
