package lk.nuvi.gemshop.dailycash;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lk.nuvi.gemshop.common.money.Currency;

/** An amount in each drawer currency (LKR, USD, EUR). */
public record Money3(BigDecimal lkr, BigDecimal usd, BigDecimal eur) {

    public static final Money3 ZERO = new Money3(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public Money3 plus(Currency c, BigDecimal v) {
        return switch (c) {
            case LKR -> new Money3(lkr.add(v), usd, eur);
            case USD -> new Money3(lkr, usd.add(v), eur);
            case EUR -> new Money3(lkr, usd, eur.add(v));
        };
    }

    public Money3 plus(Money3 o) {
        return new Money3(lkr.add(o.lkr), usd.add(o.usd), eur.add(o.eur));
    }

    public Money3 minus(Money3 o) {
        return new Money3(lkr.subtract(o.lkr), usd.subtract(o.usd), eur.subtract(o.eur));
    }

    public Money3 scaled() {
        return new Money3(s(lkr), s(usd), s(eur));
    }

    /** In LKR at the given rates. */
    public BigDecimal inLkr(BigDecimal rateUsd, BigDecimal rateEur) {
        return s(lkr.add(usd.multiply(rateUsd)).add(eur.multiply(rateEur)));
    }

    private static BigDecimal s(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
