package lk.nuvi.gemshop.system.sampledata;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.customers.CustomerRepository;
import lk.nuvi.gemshop.gems.GemRepository;
import lk.nuvi.gemshop.sales.Quote;
import lk.nuvi.gemshop.sales.QuoteRepository;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The prototype's reservations (RS-0001, RS-0002 – their gems are already "Reserved" from the gems sample data) and
 * quotations (QT-0001 active, QT-0002 expired) – P3-03. Expiry dates move with the sample dates.
 */
@Component
class QuotesSampleData implements SampleDataPart {

    private final QuoteRepository quotes;
    private final CustomerRepository customers;
    private final GemRepository gems;
    private final BranchRepository branches;
    private final UserAccountRepository users;

    QuotesSampleData(QuoteRepository quotes, CustomerRepository customers, GemRepository gems, BranchRepository branches,
            UserAccountRepository users) {
        this.quotes = quotes;
        this.customers = customers;
        this.gems = gems;
        this.branches = branches;
        this.users = users;
    }

    @Override
    public int order() {
        return 335;
    }

    @Override
    public String name() {
        return "quotes";
    }

    @Override
    public int catchUp(SampleData data) {
        return quotes.count() == 0 && customers.count() > 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        Map<String, UserAccount> people = new HashMap<>();
        for (JsonNode u : data.node("users").values()) {
            users.findByEmail(UserAccount.normalise(u.path("email").asString())).ifPresent(x -> people.put(u.path("id").asString(), x));
        }
        int n = 0;
        for (JsonNode q : data.node("quotes").values()) {
            Quote.Kind kind = "reservation".equals(q.path("kind").asString()) ? Quote.Kind.RESERVATION : Quote.Kind.QUOTATION;
            String notes = SampleData.text(q.path("notes"));
            Quote x = new Quote(q.path("id").asString(), kind, data.instant(q.path("created")),
                    branches.findByCode(q.path("branch").asString()).orElseThrow(),
                    people.get(q.path("by").asString()),
                    customers.findByCode(q.path("customerId").asString()).orElseThrow(),
                    data.date(q.path("expires")), SampleData.decimal(q.path("deposit")),
                    notes == null || notes.isBlank() ? null : notes);
            for (JsonNode i : q.path("items").values()) {
                x.add(gems.findByCode(i.path("productId").asString()).orElseThrow(), SampleData.decimal(i.path("price")));
            }
            x.setStatus(switch (q.path("status").asString("active").toLowerCase(Locale.ROOT)) {
                case "expired" -> Quote.Status.EXPIRED;
                case "converted" -> Quote.Status.CONVERTED;
                default -> Quote.Status.ACTIVE;
            });
            quotes.save(x);
            n++;
        }
        return n;
    }
}
