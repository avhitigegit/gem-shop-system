package lk.nuvi.gemshop.settings.rates;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.money.Currency;
import lk.nuvi.gemshop.settings.api.SettingsDtos.RateDay;
import lk.nuvi.gemshop.settings.api.SettingsDtos.RatesView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exchange rates (BRD A4, G2, rule 10): LKR, USD and EUR only. The owner enters the USD and EUR rate of the day;
 * the newest rate is "today's rate" for all new sales and conversions. Older rates stay as history (a sale keeps
 * the rate it was made with).
 * <pre>
 * Rates.Today t = rates.today();          // USD 300.0000, EUR 325.0000
 * Money.fromLkr(450000, Currency.USD, t.of(Currency.USD))
 * </pre>
 */
@Service
public class Rates {

    public static final String NO_RATE = "NO_RATE";
    private static final int HISTORY_DAYS = 60;

    private final ExchangeRateRepository repo;
    private final UserAccountRepository users;
    private final ActivityLog log;
    private final ZoneId zone;

    public Rates(ExchangeRateRepository repo, UserAccountRepository users, ActivityLog log, AppProperties app) {
        this.repo = repo;
        this.users = users;
        this.log = log;
        this.zone = ZoneId.of(app.timezone());
    }

    /** Today's rates. {@code updatedAt} = when the newest one was entered. */
    public record Today(BigDecimal usd, BigDecimal eur, Instant updatedAt, String updatedByEmail) {
        /** LKR for 1 USD / 1 EUR; 1 for LKR. Throws NO_RATE when the owner has not entered a rate yet. */
        public BigDecimal of(Currency c) {
            BigDecimal r = switch (c) {
                case LKR -> BigDecimal.ONE;
                case USD -> usd;
                case EUR -> eur;
            };
            if (r == null) {
                throw ApiException.conflict(NO_RATE, "Enter today's " + c + " rate first (Settings → Exchange rates).");
            }
            return r;
        }

        public boolean enteredOn(LocalDate day, ZoneId zone) {
            return updatedAt != null && updatedAt.atZone(zone).toLocalDate().equals(day);
        }
    }

    @Transactional(readOnly = true)
    public Today today() {
        ExchangeRate usd = repo.current(Currency.USD).orElse(null);
        ExchangeRate eur = repo.current(Currency.EUR).orElse(null);
        ExchangeRate newest = usd == null ? eur : eur == null ? usd : usd.getEffectiveAt().isAfter(eur.getEffectiveAt()) ? usd : eur;
        return new Today(usd == null ? null : usd.getRate(), eur == null ? null : eur.getRate(),
                newest == null ? null : newest.getEffectiveAt(), newest == null ? null : newest.getCreatedBy());
    }

    public boolean updatedToday(Today t) {
        return t.enteredOn(LocalDate.now(zone), zone);
    }

    /** Save today's USD and EUR rate (a new history row; earlier sales keep their own rate). */
    @Transactional
    public RatesView save(BigDecimal usd, BigDecimal eur) {
        Instant now = Instant.now();
        repo.save(new ExchangeRate(Currency.USD, usd, now));
        repo.save(new ExchangeRate(Currency.EUR, eur, now));
        log.add("Exchange rates updated", "USD " + usd.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " · EUR "
                + eur.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), null);
        return view();
    }

    /** The Exchange rates screen: today's rates and one line per day (the last rate of that day), newest first. */
    @Transactional(readOnly = true)
    public RatesView view() {
        Today t = today();
        Instant since = LocalDate.now(zone).minusDays(HISTORY_DAYS).atStartOfDay(zone).toInstant();
        Map<String, String> names = new HashMap<>();
        TreeMap<LocalDate, RateDay> days = new TreeMap<>();
        for (ExchangeRate r : repo.findByEffectiveAtAfterOrderByEffectiveAtAscIdAsc(since)) {
            LocalDate d = r.getEffectiveAt().atZone(zone).toLocalDate();
            RateDay old = days.get(d);
            BigDecimal usd = r.getCurrency() == Currency.USD ? r.getRate() : old == null ? null : old.usd();
            BigDecimal eur = r.getCurrency() == Currency.EUR ? r.getRate() : old == null ? null : old.eur();
            days.put(d, new RateDay(d, usd, eur, name(names, r.getCreatedBy())));
        }
        List<RateDay> history = new ArrayList<>(days.descendingMap().values());
        return new RatesView(t.usd(), t.eur(), t.updatedAt(), t.updatedByEmail() == null ? null : name(names, t.updatedByEmail()),
                history);
    }

    private String name(Map<String, String> cache, String email) {
        return cache.computeIfAbsent(email, e -> users.findByEmail(UserAccount.normalise(e)).map(UserAccount::getName)
                .orElse("system".equals(e) ? "System" : e));
    }
}
