package lk.nuvi.gemshop.system.sampledata;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import lk.nuvi.gemshop.accountant.MonthClosing;
import lk.nuvi.gemshop.accountant.MonthClosingRepository;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The prototype's closed months (state.months: 3 and 2 months before the export, closed by the accountant). They stay
 * the same number of months before today, so "last month is not closed yet" shows as in the prototype.
 */
@Component
class MonthsSampleData implements SampleDataPart {

    private final MonthClosingRepository months;
    private final UserAccountRepository users;

    MonthsSampleData(MonthClosingRepository months, UserAccountRepository users) {
        this.months = months;
        this.users = users;
    }

    @Override
    public int order() {
        return 970;
    }

    @Override
    public String name() {
        return "closed months";
    }

    @Override
    public int catchUp(SampleData data) {
        return months.count() == 0 && users.count() > 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        Map<String, UserAccount> people = new HashMap<>();
        for (JsonNode u : data.node("users").values()) {
            users.findByEmail(UserAccount.normalise(u.path("email").asString())).ifPresent(x -> people.put(u.path("id").asString(), x));
        }
        YearMonth exported = YearMonth.from(LocalDate.now().minusDays(data.shiftDays()));
        YearMonth now = YearMonth.from(LocalDate.now());
        int n = 0;
        for (Entry<String, JsonNode> e : data.node("months").properties()) {
            JsonNode m = e.getValue();
            if (!"closed".equals(m.path("status").asString())) {
                continue;
            }
            long back = ChronoUnit.MONTHS.between(YearMonth.parse(e.getKey()), exported);
            YearMonth month = now.minusMonths(back);
            if (back < 1 || months.findByMonth(month.toString()).isPresent()) {
                continue;
            }
            Instant at = data.instant(m.path("closedAt"));
            months.save(new MonthClosing(month, at == null ? Instant.now() : at, people.get(m.path("by").asString())));
            n++;
        }
        return n;
    }
}
