package lk.nuvi.gemshop.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void lkrHasRateOneAndSameForeignAmount() {
        Money m = Money.lkr("450000");
        assertThat(m.getAmountLkr()).isEqualByComparingTo("450000.00");
        assertThat(m.getCurrency()).isEqualTo(Currency.LKR);
        assertThat(m.getRate()).isEqualByComparingTo("1");
        assertThat(m.getForeignAmount()).isEqualByComparingTo("450000.00");
    }

    @Test
    void usdPaidIsChangedToLkrAtTheDaysRate() {
        Money m = Money.of(new BigDecimal("1500"), Currency.USD, new BigDecimal("300"));
        assertThat(m.getAmountLkr()).isEqualByComparingTo("450000.00");
        assertThat(m.getForeignAmount()).isEqualByComparingTo("1500.00");
    }

    @Test
    void exactDecimalsRoundHalfUpToCents() {
        Money m = Money.of(new BigDecimal("10.01"), Currency.EUR, new BigDecimal("323.55"));
        assertThat(m.getAmountLkr()).isEqualByComparingTo("3238.74"); // 3238.7355 → 3238.74
        assertThat(m.getAmountLkr().scale()).isEqualTo(2);
    }

    @Test
    void lkrPriceShownInUsd() {
        Money m = Money.fromLkr(new BigDecimal("950000"), Currency.USD, new BigDecimal("300"));
        assertThat(m.getForeignAmount()).isEqualByComparingTo("3166.67");
        assertThat(m.getAmountLkr()).isEqualByComparingTo("950000.00");
    }

    @Test
    void rateMustBePositive() {
        assertThatThrownBy(() -> Money.of(BigDecimal.TEN, Currency.USD, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyThreeCurrencies() {
        assertThat(Currency.values()).containsExactly(Currency.LKR, Currency.USD, Currency.EUR);
    }
}
