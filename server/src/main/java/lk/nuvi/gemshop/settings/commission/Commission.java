package lk.nuvi.gemshop.settings.commission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.common.money.Money;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commission rules (BRD A6, H1, H2, rule 5): a % of the sale for guides / drivers, and a fixed amount by price level
 * for staff. Changes apply to new sales (each sale keeps the commission worked out when it was made – P3-05).
 * <pre>
 * commission.staffAmount(saleTotalLkr)       // 300,000 → 3,000 (level 3)
 * commission.guideAmount(saleTotalLkr, pct)  // 300,000 at 10% → 30,000
 * </pre>
 */
@Service
public class Commission {

    public static final BigDecimal MAX_GUIDE_PCT = new BigDecimal("50");
    public static final int MAX_LEVELS = 10;
    public static final String LEVELS_ORDER = "LEVELS_ORDER";

    private final CommissionRulesRepository rules;
    private final CommissionLevelRepository levels;
    private final ActivityLog log;

    public Commission(CommissionRulesRepository rules, CommissionLevelRepository levels, ActivityLog log) {
        this.rules = rules;
        this.levels = levels;
        this.log = log;
    }

    /** One staff level. {@code upTo} empty = the last level ("above …"). */
    public record Level(BigDecimal upTo, BigDecimal amount) {}

    public record Rules(BigDecimal defaultGuidePct, List<Level> levels) {}

    /** The levels in the BRD example (page 9) – "Use BRD example levels" and a new database. */
    public static final List<Level> BRD_EXAMPLE = List.of(
            new Level(new BigDecimal("50000.00"), new BigDecimal("500.00")),
            new Level(new BigDecimal("200000.00"), new BigDecimal("1500.00")),
            new Level(new BigDecimal("500000.00"), new BigDecimal("3000.00")),
            new Level(null, new BigDecimal("5000.00")));

    @Transactional(readOnly = true)
    public Rules current() {
        BigDecimal pct = rules.current().map(CommissionRules::getDefaultGuidePct).orElse(BigDecimal.TEN.setScale(2));
        List<Level> list = levels.findAllByOrderByLevelNoAsc().stream().map(l -> new Level(l.getUpTo(), l.getAmount())).toList();
        return new Rules(pct, list.isEmpty() ? BRD_EXAMPLE : list);
    }

    /** Staff commission for a sale total (the level whose "up to" the total does not pass). */
    @Transactional(readOnly = true)
    public BigDecimal staffAmount(BigDecimal saleTotal) {
        List<Level> list = current().levels();
        return list.get(levelIndex(list, saleTotal)).amount();
    }

    /** 0-based level for a sale total. */
    public static int levelIndex(List<Level> list, BigDecimal saleTotal) {
        for (int i = 0; i < list.size(); i++) {
            Level l = list.get(i);
            if (l.upTo() == null || saleTotal.compareTo(l.upTo()) <= 0) {
                return i;
            }
        }
        return list.size() - 1;
    }

    /** Guide / driver commission: sale total × % (2 decimals). */
    public static BigDecimal guideAmount(BigDecimal saleTotal, BigDecimal pct) {
        return saleTotal.multiply(pct).divide(BigDecimal.valueOf(100), Money.SCALE, RoundingMode.HALF_UP);
    }

    /** Save the owner's rules. Levels must go up; the last one has no "up to". */
    @Transactional
    public Rules save(BigDecimal defaultGuidePct, List<Level> next) {
        check(defaultGuidePct, next);
        BigDecimal pct = defaultGuidePct.setScale(2, RoundingMode.HALF_UP);
        CommissionRules r = rules.current().orElseGet(() -> rules.save(new CommissionRules(pct)));
        r.setDefaultGuidePct(pct);
        levels.deleteAllLevels();
        List<CommissionLevel> saved = new ArrayList<>();
        for (int i = 0; i < next.size(); i++) {
            Level l = next.get(i);
            BigDecimal upTo = i == next.size() - 1 ? null : l.upTo().setScale(2, RoundingMode.HALF_UP);
            saved.add(new CommissionLevel(i + 1, upTo, l.amount().setScale(2, RoundingMode.HALF_UP)));
        }
        levels.saveAll(saved);
        log.add("Commission rules saved", "Guides " + pct.stripTrailingZeros().toPlainString() + "% · staff "
                + saved.stream().map(l -> (l.getUpTo() == null ? "above" : "≤" + l.getUpTo().toBigInteger()) + ": "
                        + l.getAmount().toBigInteger()).collect(Collectors.joining(" · ")), null);
        return current();
    }

    private static void check(BigDecimal pct, List<Level> list) {
        if (pct == null || pct.signum() < 0 || pct.compareTo(MAX_GUIDE_PCT) > 0) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Guide % must be 0 to 50.",
                    "defaultGuidePct", "0 to 50.");
        }
        if (list == null || list.size() < 2 || list.size() > MAX_LEVELS) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Staff commission needs 2 to " + MAX_LEVELS + " levels.");
        }
        BigDecimal before = BigDecimal.ZERO;
        for (int i = 0; i < list.size(); i++) {
            Level l = list.get(i);
            if (l.amount() == null || l.amount().signum() < 0) {
                throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Amounts cannot be below 0.",
                        "levels[" + i + "].amount", "0 or more.");
            }
            if (i == list.size() - 1) {
                break;
            }
            if (l.upTo() == null || l.upTo().compareTo(before) <= 0) {
                throw ApiException.field(HttpStatus.BAD_REQUEST, LEVELS_ORDER,
                        "Levels must go up: each “up to” amount must be higher than the one before.",
                        "levels[" + i + "].upTo", "Higher than the level before.");
            }
            before = l.upTo();
        }
    }
}
