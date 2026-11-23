package lk.nuvi.gemshop.system.sampledata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.dailycash.CashOpening;
import lk.nuvi.gemshop.dailycash.CashOpeningRepository;
import lk.nuvi.gemshop.dailycash.DayClosing;
import lk.nuvi.gemshop.dailycash.DayClosingRepository;
import lk.nuvi.gemshop.dailycash.Money3;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.SaleRepository;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.settings.rates.Rates;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The prototype's opening cash (the last 10 days and today, Ella: LKR 25,000 · USD 100 · EUR 50) and day closings (the last
 * 10 days; 4 days ago LKR 2,500 short, 7 days ago USD 20 short) – P3-06. Expected / counted amounts as in the prototype;
 * bills, sales total and card fee from the sample sales of that day.
 */
@Component
class DailyCashSampleData implements SampleDataPart {

    private final CashOpeningRepository openings;
    private final DayClosingRepository closings;
    private final BranchRepository branches;
    private final UserAccountRepository users;
    private final SaleRepository sales;
    private final Rates rates;
    private final ZoneId zone;

    DailyCashSampleData(CashOpeningRepository openings, DayClosingRepository closings, BranchRepository branches, UserAccountRepository users,
            SaleRepository sales, Rates rates, AppProperties app) {
        this.openings = openings;
        this.closings = closings;
        this.branches = branches;
        this.users = users;
        this.sales = sales;
        this.rates = rates;
        this.zone = ZoneId.of(app.timezone());
    }

    @Override
    public int order() {
        return 350;
    }

    @Override
    public String name() {
        return "daily cash";
    }

    @Override
    public int catchUp(SampleData data) {
        return openings.count() == 0 && closings.count() == 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        JsonNode state = data.node("openings").isMissingNode() ? null : data.node("openings");
        Map<String, UserAccount> people = new HashMap<>();
        for (JsonNode u : data.node("users").values()) {
            users.findByEmail(UserAccount.normalise(u.path("email").asString())).ifPresent(x -> people.put(u.path("id").asString(), x));
        }
        int n = 0;
        if (state != null) {
            for (var e : state.properties()) {
                String[] key = e.getKey().split("\\|");
                Branch b = branches.findByCode(key[1]).orElse(null);
                JsonNode o = e.getValue();
                UserAccount by = people.get(o.path("by").asString());
                if (b == null || by == null) {
                    continue;
                }
                LocalDate day = LocalDate.parse(key[0]).plusDays(data.shiftDays());
                CashOpening x = new CashOpening(b, day);
                x.set(money(o), data.instant(o.path("at")), by);
                openings.save(x);
                n++;
            }
        }
        Rates.Today r = rates.today();
        for (JsonNode c : data.node("closings").values()) {
            Branch b = branches.findByCode(c.path("branch").asString()).orElse(null);
            UserAccount by = people.get(c.path("by").asString());
            if (b == null || by == null) {
                continue;
            }
            LocalDate day = LocalDate.parse(c.path("date").asString()).plusDays(data.shiftDays());
            Instant from = day.atStartOfDay(zone).toInstant();
            Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
            BigDecimal total = BigDecimal.ZERO;
            BigDecimal fee = BigDecimal.ZERO;
            int bills = 0;
            for (Sale s : sales.search(b.getId(), from, to, Sale.Status.COMPLETED)) {
                total = total.add(s.getTotal());
                fee = fee.add(s.getCardFee());
                bills++;
            }
            Money3 expected = money(c.path("expected"));
            Money3 counted = money(c.path("counted"));
            BigDecimal cardExpected = SampleData.decimal(c.path("cardExpected")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cardCounted = SampleData.decimal(c.path("cardCounted")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal diff = counted.minus(expected).inLkr(r.usd(), r.eur()).add(cardCounted.subtract(cardExpected));
            String note = c.path("note").asString("");
            DayClosing x = new DayClosing("DC-" + DateTimeFormatter.BASIC_ISO_DATE.format(day), b, day, new DayClosing.Figures(expected, counted,
                    cardExpected, cardCounted, fee, total, bills, BigDecimal.ZERO, r.usd(), r.eur(), diff), note.isBlank() ? null : note,
                    data.instant(c.path("at")), by);
            if (c.path("sentToOwner").asBoolean(false)) {
                x.sent();
            }
            closings.save(x);
            n++;
        }
        return n;
    }

    private static Money3 money(JsonNode o) {
        return new Money3(SampleData.decimal(o.path("LKR")), SampleData.decimal(o.path("USD")), SampleData.decimal(o.path("EUR"))).scaled();
    }
}
