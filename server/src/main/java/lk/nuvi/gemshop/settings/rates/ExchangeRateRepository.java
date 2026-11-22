package lk.nuvi.gemshop.settings.rates;

import java.util.Optional;
import lk.nuvi.gemshop.common.money.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    /** Today's rate = the newest one. */
    Optional<ExchangeRate> findFirstByCurrencyOrderByEffectiveAtDescIdDesc(Currency currency);

    default Optional<ExchangeRate> current(Currency currency) {
        return findFirstByCurrencyOrderByEffectiveAtDescIdDesc(currency);
    }
}
