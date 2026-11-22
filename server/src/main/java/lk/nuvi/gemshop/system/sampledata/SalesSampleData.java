package lk.nuvi.gemshop.system.sampledata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.commissions.Guide;
import lk.nuvi.gemshop.commissions.GuideRepository;
import lk.nuvi.gemshop.common.money.Currency;
import lk.nuvi.gemshop.customers.Customer;
import lk.nuvi.gemshop.customers.CustomerRepository;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemRepository;
import lk.nuvi.gemshop.sales.HeldSale;
import lk.nuvi.gemshop.sales.HeldSaleRepository;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.SalePayment;
import lk.nuvi.gemshop.sales.SaleRepository;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The prototype's 25 customers, 6 guides & drivers, 40 sales (with their payments) and the held sale HS-0001 (waiting for
 * AP-0010) – P3-01. The sold gems are already marked Sold by the gems sample data; the counters by NumbersSampleData.
 */
@Component
class SalesSampleData implements SampleDataPart {

    private final CustomerRepository customers;
    private final GuideRepository guides;
    private final SaleRepository sales;
    private final HeldSaleRepository held;
    private final GemRepository gems;
    private final BranchRepository branches;
    private final UserAccountRepository users;

    SalesSampleData(CustomerRepository customers, GuideRepository guides, SaleRepository sales, HeldSaleRepository held,
            GemRepository gems, BranchRepository branches, UserAccountRepository users) {
        this.customers = customers;
        this.guides = guides;
        this.sales = sales;
        this.held = held;
        this.gems = gems;
        this.branches = branches;
        this.users = users;
    }

    @Override
    public int order() {
        return 330;
    }

    @Override
    public String name() {
        return "sales";
    }

    @Override
    public int catchUp(SampleData data) {
        return customers.count() == 0 && sales.count() == 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        Map<String, UserAccount> people = new HashMap<>();
        for (JsonNode u : data.node("users").values()) {
            users.findByEmail(UserAccount.normalise(u.path("email").asString())).ifPresent(x -> people.put(u.path("id").asString(), x));
        }
        int n = 0;
        Map<String, Customer> cs = new HashMap<>();
        for (JsonNode c : data.node("customers").values()) {
            String bday = blank(c.path("birthday")) == null ? null : data.birthday(c.path("birthday"));
            Customer x = new Customer(c.path("id").asString(), new Customer.Details(c.path("name").asString(),
                    c.path("country").asString(), blank(c.path("email")), blank(c.path("whatsapp")), bday,
                    c.path("consent").asBoolean(false)), data.instant(c.path("createdAt")))
                    .extra(data.instant(c.path("consentAt")), c.path("unsubscribed").asBoolean(false), blank(c.path("passport")),
                            blank(c.path("notes")));
            cs.put(x.getCode(), customers.save(x));
            n++;
        }
        Map<String, Guide> gs = new HashMap<>();
        for (JsonNode g : data.node("guides").values()) {
            String since = blank(g.path("since"));
            Guide x = new Guide(g.path("id").asString(), new Guide.Details(g.path("name").asString(), blank(g.path("vehicle")),
                    blank(g.path("vehicleNo")), g.path("phone").asString(), SampleData.decimal(g.path("pct")),
                    since == null ? null : LocalDate.parse(since)));
            gs.put(x.getCode(), guides.save(x));
            n++;
        }
        for (JsonNode s : data.node("sales").values()) {
            BigDecimal usd = null;
            BigDecimal eur = null;
            for (JsonNode p : s.path("payments").values()) {
                if ("USD".equals(p.path("currency").asString())) {
                    usd = SampleData.decimal(p.path("rate"));
                } else if ("EUR".equals(p.path("currency").asString())) {
                    eur = SampleData.decimal(p.path("rate"));
                }
            }
            Sale.Money m = new Sale.Money(SampleData.decimal(s.path("total")), SampleData.decimal(s.path("guidePct")),
                    SampleData.decimal(s.path("guideComm")), SampleData.decimal(s.path("staffComm")),
                    SampleData.decimal(s.path("cardFee")), money(s.path("change")), usd, eur);
            Sale x = new Sale(s.path("id").asString(), data.instant(s.path("date")), branch(s.path("branch").asString()),
                    cs.get(s.path("customerId").asString()), gs.get(blank(s.path("guideId"))), people.get(s.path("staffId").asString()),
                    m, null, blank(s.path("notes")));
            for (JsonNode i : s.path("items").values()) {
                x.add(gem(i.path("productId").asString()), SampleData.decimal(i.path("listPrice")), SampleData.decimal(i.path("agreed")),
                        SampleData.decimal(i.path("minPrice")), blank(i.path("approvalId")));
                if (i.path("returned").asBoolean(false)) {
                    x.getItems().get(x.getItems().size() - 1).release();
                }
            }
            for (JsonNode p : s.path("payments").values()) {
                boolean card = "card".equals(p.path("method").asString());
                x.pay(card ? SalePayment.Method.CARD : SalePayment.Method.CASH,
                        Currency.valueOf(p.path("currency").asString().toUpperCase(Locale.ROOT)), SampleData.decimal(p.path("amount")),
                        SampleData.decimal(p.path("rate")), SampleData.decimal(p.path("lkr")), card ? money(p.path("feePct")) : BigDecimal.ZERO,
                        money(p.path("fee")));
            }
            x.sent(s.path("sentEmail").asBoolean(false), s.path("sentWhatsapp").asBoolean(false));
            sales.save(x);
            n++;
        }
        for (JsonNode h : data.node("held").values()) {
            HeldSale x = new HeldSale(h.path("id").asString(), data.instant(h.path("at")), branch(h.path("branch").asString()),
                    people.get(h.path("staffId").asString()), cs.get(h.path("customer").path("id").asString()),
                    gs.get(blank(h.path("guideId"))));
            for (JsonNode i : h.path("items").values()) {
                x.add(gem(i.path("productId").asString()), SampleData.decimal(i.path("listPrice")), SampleData.decimal(i.path("agreed")),
                        SampleData.decimal(i.path("minPrice")));
            }
            x.askedIn(blank(h.path("approvalId")));
            x.setStatus(HeldSale.Status.valueOf(h.path("status").asString("pending").toUpperCase(Locale.ROOT)));
            held.save(x);
            n++;
        }
        return n;
    }

    private static BigDecimal money(JsonNode n) {
        return n.isMissingNode() || n.isNull() ? BigDecimal.ZERO : SampleData.decimal(n);
    }

    private Branch branch(String code) {
        return branches.findByCode(code).orElseThrow(() -> new IllegalStateException("Sample data: unknown branch '" + code + "'"));
    }

    private Gem gem(String code) {
        return gems.findByCode(code).orElseThrow(() -> new IllegalStateException("Sample data: unknown gem '" + code + "'"));
    }

    private static String blank(JsonNode n) {
        String s = SampleData.text(n);
        return s == null || s.isBlank() ? null : s;
    }
}
