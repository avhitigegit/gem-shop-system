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

/**
 * Day-end closing (DC-…, BRD I3, I4): counted cash per currency and the card machine total against what the system
 * expected. Once saved the day is locked for that branch – it is never changed.
 */
@Entity
@Table(name = "day_closing")
public class DayClosing extends BaseEntity {

    /** What the closing records, worked out when the day is closed. */
    public record Figures(Money3 expected, Money3 counted, BigDecimal cardExpected, BigDecimal cardCounted, BigDecimal cardFee,
            BigDecimal salesTotal, int bills, BigDecimal drawerOut, BigDecimal rateUsd, BigDecimal rateEur, BigDecimal difference) {}

    @Column(nullable = false, length = 30, updatable = false)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", updatable = false)
    private Branch branch;

    @Column(nullable = false, updatable = false)
    private LocalDate day;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal expectedLkr;
    @Column(nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal expectedUsd;
    @Column(nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal expectedEur;
    @Column(nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal countedLkr;
    @Column(nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal countedUsd;
    @Column(nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal countedEur;
    @Column(nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal cardExpected;
    @Column(nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal cardCounted;
    @Column(nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal cardFee;
    @Column(nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal salesTotal;
    @Column(nullable = false, updatable = false) private int bills;
    @Column(nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal drawerOut;
    @Column(nullable = false, precision = 12, scale = 4, updatable = false) private BigDecimal rateUsd;
    @Column(nullable = false, precision = 12, scale = 4, updatable = false) private BigDecimal rateEur;
    @Column(nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal difference;

    @Column(length = 500, updatable = false)
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "closed_by_id", updatable = false)
    private UserAccount closedBy;

    @Column(nullable = false)
    private boolean sentToOwner;

    protected DayClosing() {}

    public DayClosing(String number, Branch branch, LocalDate day, Figures f, String note, Instant closedAt, UserAccount closedBy) {
        this.number = number;
        this.branch = branch;
        this.day = day;
        this.expectedLkr = f.expected().lkr();
        this.expectedUsd = f.expected().usd();
        this.expectedEur = f.expected().eur();
        this.countedLkr = f.counted().lkr();
        this.countedUsd = f.counted().usd();
        this.countedEur = f.counted().eur();
        this.cardExpected = f.cardExpected();
        this.cardCounted = f.cardCounted();
        this.cardFee = f.cardFee();
        this.salesTotal = f.salesTotal();
        this.bills = f.bills();
        this.drawerOut = f.drawerOut();
        this.rateUsd = f.rateUsd();
        this.rateEur = f.rateEur();
        this.difference = f.difference();
        this.note = note;
        this.closedAt = closedAt;
        this.closedBy = closedBy;
    }

    public void sent() {
        this.sentToOwner = true;
    }

    public Money3 expected() { return new Money3(expectedLkr, expectedUsd, expectedEur); }
    public Money3 counted() { return new Money3(countedLkr, countedUsd, countedEur); }
    public String getNumber() { return number; }
    public Branch getBranch() { return branch; }
    public LocalDate getDay() { return day; }
    public BigDecimal getCardExpected() { return cardExpected; }
    public BigDecimal getCardCounted() { return cardCounted; }
    public BigDecimal getCardFee() { return cardFee; }
    public BigDecimal getSalesTotal() { return salesTotal; }
    public int getBills() { return bills; }
    public BigDecimal getDrawerOut() { return drawerOut; }
    public BigDecimal getRateUsd() { return rateUsd; }
    public BigDecimal getRateEur() { return rateEur; }
    public BigDecimal getDifference() { return difference; }
    public String getNote() { return note; }
    public Instant getClosedAt() { return closedAt; }
    public UserAccount getClosedBy() { return closedBy; }
    public boolean isSentToOwner() { return sentToOwner; }
}
