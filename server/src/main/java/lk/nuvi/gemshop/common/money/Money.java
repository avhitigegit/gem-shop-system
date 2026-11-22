package lk.nuvi.gemshop.common.money;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount of money, always exact (never double).
 * <ul>
 *   <li>{@code amountLkr} – the value in LKR, used for every total, report and profit</li>
 *   <li>{@code currency} – what was actually paid / quoted: LKR, USD or EUR</li>
 *   <li>{@code rate} – LKR for 1 unit of the currency on that day (1 for LKR)</li>
 *   <li>{@code foreignAmount} – the amount in that currency (same as amountLkr for LKR)</li>
 * </ul>
 * Columns (use {@code @AttributeOverrides} for a prefix, e.g. {@code paid_amount_lkr}):
 * {@code amount_lkr numeric(14,2), currency varchar(3), rate numeric(12,4), foreign_amount numeric(14,2)}.
 */
@Embeddable
public class Money {

    public static final int SCALE = 2;
    public static final int RATE_SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    @Column(name = "amount_lkr", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountLkr;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "rate", nullable = false, precision = 12, scale = 4)
    private BigDecimal rate;

    @Column(name = "foreign_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal foreignAmount;

    protected Money() {}

    private Money(BigDecimal amountLkr, Currency currency, BigDecimal rate, BigDecimal foreignAmount) {
        this.amountLkr = amountLkr;
        this.currency = currency;
        this.rate = rate;
        this.foreignAmount = foreignAmount;
    }

    /** An amount in LKR. */
    public static Money lkr(BigDecimal amount) {
        BigDecimal a = money(amount);
        return new Money(a, Currency.LKR, BigDecimal.ONE.setScale(RATE_SCALE), a);
    }

    public static Money lkr(String amount) {
        return lkr(new BigDecimal(amount));
    }

    /**
     * An amount paid or quoted in {@code currency} at {@code rate} (LKR per unit).
     * The LKR value is {@code foreignAmount × rate}, rounded half-up to cents.
     */
    public static Money of(BigDecimal foreignAmount, Currency currency, BigDecimal rate) {
        Objects.requireNonNull(currency, "currency");
        if (currency == Currency.LKR) {
            return lkr(foreignAmount);
        }
        BigDecimal r = checkRate(rate);
        BigDecimal f = money(foreignAmount);
        return new Money(f.multiply(r).setScale(SCALE, ROUNDING), currency, r, f);
    }

    /** Shows an LKR price in another currency at the day's rate (e.g. LKR 450,000 at 300 = USD 1,500.00). */
    public static Money fromLkr(BigDecimal amountLkr, Currency currency, BigDecimal rate) {
        Objects.requireNonNull(currency, "currency");
        if (currency == Currency.LKR) {
            return lkr(amountLkr);
        }
        BigDecimal r = checkRate(rate);
        BigDecimal a = money(amountLkr);
        return new Money(a, currency, r, a.divide(r, SCALE, ROUNDING));
    }

    public BigDecimal getAmountLkr() {
        return amountLkr;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getForeignAmount() {
        return foreignAmount;
    }

    public boolean isZero() {
        return amountLkr.signum() == 0;
    }

    private static BigDecimal money(BigDecimal v) {
        return Objects.requireNonNull(v, "amount").setScale(SCALE, ROUNDING);
    }

    private static BigDecimal checkRate(BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("Exchange rate must be more than 0");
        }
        return rate.setScale(RATE_SCALE, ROUNDING);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money m
                && amountLkr.compareTo(m.amountLkr) == 0
                && currency == m.currency
                && rate.compareTo(m.rate) == 0
                && foreignAmount.compareTo(m.foreignAmount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountLkr.stripTrailingZeros(), currency, rate.stripTrailingZeros(), foreignAmount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return currency == Currency.LKR ? "LKR " + amountLkr.toPlainString()
                : currency + " " + foreignAmount.toPlainString() + " @ " + rate.toPlainString() + " = LKR " + amountLkr.toPlainString();
    }
}
