package lk.nuvi.gemshop.system.sampledata;

import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.gems.CertType;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemHistory;
import lk.nuvi.gemshop.gems.GemHistoryRepository;
import lk.nuvi.gemshop.gems.GemRepository;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** The prototype's 87 gems with prices, certificates, status and history (P2-01). No photos: the gem is drawn. */
@Component
class GemsSampleData implements SampleDataPart {

    private final GemRepository gems;
    private final GemHistoryRepository history;
    private final BranchRepository branches;
    private final UserAccountRepository users;
    private final Numbers numbers;

    GemsSampleData(GemRepository gems, GemHistoryRepository history, BranchRepository branches,
            UserAccountRepository users, Numbers numbers) {
        this.gems = gems;
        this.history = history;
        this.branches = branches;
        this.users = users;
        this.numbers = numbers;
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public String name() {
        return "gems";
    }

    @Override
    public int catchUp(SampleData data) {
        return gems.count() == 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        Map<String, UserAccount> people = new HashMap<>();
        for (JsonNode u : data.node("users").values()) {
            users.findByEmail(UserAccount.normalise(u.path("email").asString())).ifPresent(x -> people.put(u.path("id").asString(), x));
        }
        Map<String, Branch> shops = new HashMap<>();
        int n = 0;
        long last = 0;
        for (JsonNode p : data.node("products").values()) {
            String code = p.path("id").asString();
            JsonNode c = p.path("cert");
            CertType ct = CertType.valueOf(c.path("type").asString().toUpperCase(Locale.ROOT));
            Gem.Details d = new Gem.Details(p.path("name").asString(), p.path("type").asString(), p.path("gem").asString(),
                    SampleData.decimal(p.path("carat")).setScale(2, RoundingMode.HALF_UP), p.path("colour").asString(),
                    p.path("shape").asString(), p.path("treatment").asString(), SampleData.text(p.path("description")));
            Gem.Certificate cert = new Gem.Certificate(ct, blank(c.path("lab")), blank(c.path("no")),
                    ct == CertType.LAB && !c.path("date").asString("").isBlank() ? data.date(c.path("date")) : null, null);
            Gem.Prices prices = new Gem.Prices(SampleData.decimal(p.path("cost")), SampleData.decimal(p.path("price")),
                    SampleData.decimal(p.path("min")));
            String b = p.path("branch").asString();
            Branch branch = shops.computeIfAbsent(b, x -> branches.findByCode(x)
                    .orElseThrow(() -> new IllegalStateException("Sample data: unknown branch '" + x + "'")));
            GemStatus status = GemStatus.valueOf(p.path("status").asString().toUpperCase(Locale.ROOT));
            Gem g = new Gem(code, d, cert, prices, branch, status, data.instant(p.path("addedAt")),
                    people.get(p.path("addedBy").asString()));
            g.boughtIn(blank(p.path("supplierId")), blank(p.path("purchaseId")));
            if (status == GemStatus.SOLD) {
                g.sold(SampleData.text(p.path("saleId")), data.instant(p.path("soldAt")));
            }
            gems.save(g);
            for (JsonNode h : p.path("history").values()) {
                UserAccount who = people.get(h.path("user").asString());
                history.save(new GemHistory(g, data.instant(h.path("at")), who, who == null ? "System" : who.getName(),
                        h.path("action").asString(), AuditSampleData.shift(h.path("detail").asString(""), data)));
            }
            last = Math.max(last, Long.parseLong(code.substring(code.indexOf('-') + 1)));
            n++;
        }
        long seq = data.node("seq").path("product").asLong(0);
        numbers.continueAfter(Counter.GEM, Math.max(last, seq));
        return n;
    }

    private static String blank(JsonNode n) {
        String s = SampleData.text(n);
        return s == null || s.isBlank() ? null : s;
    }
}
