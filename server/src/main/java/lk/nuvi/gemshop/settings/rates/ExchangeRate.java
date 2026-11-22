package lk.nuvi.gemshop.settings.rates;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lk.nuvi.gemshop.common.money.Currency;
import lk.nuvi.gemshop.common.money.Money;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/** LKR for 1 USD or 1 EUR, entered by the owner. The newest row is today's rate (Settings → Exchange rates, P1-06). */
@Entity
@Table(name = "exchange_rate")
public class ExchangeRate extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal rate;

    @Column(nullable = false)
    private Instant effectiveAt;

    protected ExchangeRate() {}

    public ExchangeRate(Currency currency, BigDecimal rate, Instant effectiveAt) {
        if (currency == Currency.LKR) {
            throw new IllegalArgumentException("LKR has no exchange rate");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("Exchange rate must be more than 0");
        }
        this.currency = currency;
        this.rate = rate.setScale(Money.RATE_SCALE, Money.ROUNDING);
        this.effectiveAt = effectiveAt;
    }

    public Currency getCurrency() { return currency; }
    public BigDecimal getRate() { return rate; }
    public Instant getEffectiveAt() { return effectiveAt; }
}
