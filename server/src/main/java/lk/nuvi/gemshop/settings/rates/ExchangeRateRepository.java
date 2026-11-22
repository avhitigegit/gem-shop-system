package lk.nuvi.gemshop.settings.rates;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lk.nuvi.gemshop.common.money.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    /** Today's rate = the newest one. */
    Optional<ExchangeRate> findFirstByCurrencyOrderByEffectiveAtDescIdDesc(Currency currency);

    /** Rate history from a moment on (Settings → Exchange rates). */
    List<ExchangeRate> findByEffectiveAtAfterOrderByEffectiveAtAscIdAsc(Instant since);

    default Optional<ExchangeRate> current(Currency currency) {
        return findFirstByCurrencyOrderByEffectiveAtDescIdDesc(currency);
    }
}
