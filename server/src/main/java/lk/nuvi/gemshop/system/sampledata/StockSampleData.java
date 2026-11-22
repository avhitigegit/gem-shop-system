package lk.nuvi.gemshop.system.sampledata;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemRepository;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.stock.StockCheck;
import lk.nuvi.gemshop.stock.StockCheckRepository;
import lk.nuvi.gemshop.stock.StockCorrection;
import lk.nuvi.gemshop.stock.StockCorrectionRepository;
import lk.nuvi.gemshop.stock.Transfer;
import lk.nuvi.gemshop.stock.TransferRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The prototype's transfer TR-0001 (on the way to Branch 2), stock corrections SC-0001 (approved, AP-0002) and SC-0002
 * (waiting, AP-0013) and stock check SCK-0001 (P2-04). The counters are raised by {@code NumbersSampleData}.
 */
@Component
class StockSampleData implements SampleDataPart {

    private final TransferRepository transfers;
    private final StockCorrectionRepository corrections;
    private final StockCheckRepository checks;
    private final GemRepository gems;
    private final BranchRepository branches;
    private final UserAccountRepository users;

    StockSampleData(TransferRepository transfers, StockCorrectionRepository corrections, StockCheckRepository checks,
            GemRepository gems, BranchRepository branches, UserAccountRepository users) {
        this.transfers = transfers;
        this.corrections = corrections;
        this.checks = checks;
        this.gems = gems;
        this.branches = branches;
        this.users = users;
    }

    @Override
    public int order() {
        return 320;
    }

    @Override
    public String name() {
        return "stock";
    }

    @Override
    public int catchUp(SampleData data) {
        return transfers.count() == 0 && corrections.count() == 0 && checks.count() == 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        Map<String, UserAccount> people = new HashMap<>();
        for (JsonNode u : data.node("users").values()) {
            users.findByEmail(UserAccount.normalise(u.path("email").asString())).ifPresent(x -> people.put(u.path("id").asString(), x));
        }
        int n = 0;
        for (JsonNode t : data.node("transfers").values()) {
            Transfer x = new Transfer(t.path("id").asString(), branch(t.path("from").asString()), branch(t.path("to").asString()),
                    data.instant(t.path("sentAt")), people.get(t.path("sentBy").asString()), blank(t.path("notes")));
            for (JsonNode i : t.path("items").values()) {
                x.add(gem(i.asString()));
            }
            if ("received".equals(t.path("status").asString())) {
                x.received(data.instant(t.path("receivedAt")), people.get(t.path("receivedBy").asString()));
            }
            transfers.save(x);
            n++;
        }
        for (JsonNode c : data.node("corrections").values()) {
            StockCorrection.Kind kind = StockCorrection.Kind.valueOf(c.path("kind").asString().toUpperCase(Locale.ROOT));
            StockCorrection x = new StockCorrection(c.path("id").asString(), gem(c.path("productId").asString()), kind,
                    value(kind, c.path("from")), value(kind, c.path("to")), AuditSampleData.shift(c.path("reason").asString(""), data),
                    data.instant(c.path("at")), people.get(c.path("by").asString()));
            x.askedIn(blank(c.path("approvalId")));
            String status = c.path("status").asString("pending");
            if (!"pending".equals(status)) {
                x.decided("approved".equals(status));
            }
            corrections.save(x);
            n++;
        }
        for (JsonNode c : data.node("stockChecks").values()) {
            checks.save(new StockCheck(c.path("id").asString(), branch(c.path("branch").asString()), data.instant(c.path("date")),
                    people.get(c.path("by").asString()), c.path("expected").asInt(), c.path("found").asInt(), codes(c.path("missing")),
                    codes(c.path("wrong")), List.of(), blank(c.path("notes"))));
            n++;
        }
        return n;
    }

    /** Status values in upper case (WRITTEN_OFF), carats with two decimals (5.10), branch codes as they are. */
    private static String value(StockCorrection.Kind kind, JsonNode v) {
        return switch (kind) {
            case STATUS -> v.asString().toUpperCase(Locale.ROOT);
            case CARAT -> SampleData.decimal(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
            case BRANCH -> v.asString();
        };
    }

    private static List<String> codes(JsonNode list) {
        List<String> out = new ArrayList<>();
        list.values().forEach(x -> out.add(x.asString()));
        return out;
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
