package lk.nuvi.gemshop.system.sampledata;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.sales.CreditNote;
import lk.nuvi.gemshop.sales.CreditNoteRepository;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.SaleItem;
import lk.nuvi.gemshop.sales.SaleRepository;
import lk.nuvi.gemshop.sales.SaleReturn;
import lk.nuvi.gemshop.sales.SaleReturnRepository;
import lk.nuvi.gemshop.settings.commission.Commission;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The prototype's returns – RT-0001 (approved refund, credit note CN-0001; GEM-00121 is already "Returned" and its invoice
 * line released by the earlier parts) and RT-0002 (exchange waiting for the owner, AP-0011) – P3-04. The approvals
 * themselves come with the audit sample data (ref = RT-…); the counters with NumbersSampleData.
 */
@Component
class ReturnsSampleData implements SampleDataPart {

    private final SaleReturnRepository returns;
    private final CreditNoteRepository creditNotes;
    private final SaleRepository sales;
    private final UserAccountRepository users;
    private final Commission commission;

    ReturnsSampleData(SaleReturnRepository returns, CreditNoteRepository creditNotes, SaleRepository sales, UserAccountRepository users,
            Commission commission) {
        this.returns = returns;
        this.creditNotes = creditNotes;
        this.sales = sales;
        this.users = users;
        this.commission = commission;
    }

    @Override
    public int order() {
        return 340;
    }

    @Override
    public String name() {
        return "returns";
    }

    @Override
    public int catchUp(SampleData data) {
        return returns.count() == 0 && sales.count() > 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        Map<String, UserAccount> people = new HashMap<>();
        for (JsonNode u : data.node("users").values()) {
            users.findByEmail(UserAccount.normalise(u.path("email").asString())).ifPresent(x -> people.put(u.path("id").asString(), x));
        }
        Map<String, JsonNode> notes = new HashMap<>();
        for (JsonNode c : data.node("creditNotes").values()) {
            notes.put(c.path("returnId").asString(), c);
        }
        int n = 0;
        for (JsonNode r : data.node("returns").values()) {
            Sale s = sales.findByNumber(r.path("saleId").asString()).orElse(null);
            if (s == null) {
                continue;
            }
            String gem = r.path("productId").asString();
            SaleItem item = s.getItems().stream().filter(i -> i.getGem().getCode().equals(gem)).findFirst().orElse(null);
            if (item == null) {
                continue;
            }
            SaleReturn x = new SaleReturn(r.path("id").asString(), data.instant(r.path("at")), s, item,
                    "exchange".equals(r.path("kind").asString()) ? SaleReturn.Kind.EXCHANGE : SaleReturn.Kind.REFUND,
                    r.path("reason").asString(), people.get(r.path("by").asString()));
            x.asked(SampleData.text(r.path("approvalId")));
            String status = r.path("status").asString("pending");
            if (!"pending".equals(status)) {
                x.decided("approved".equals(status));
            }
            returns.save(x);
            JsonNode c = notes.get(x.getNumber());
            if (c != null) {
                // commissions taken back: the sale with and without this gem (the sale total still includes it)
                BigDecimal after = s.getTotal().subtract(item.getAgreed());
                BigDecimal pct = s.getGuide() == null ? BigDecimal.ZERO : s.getGuidePct();
                BigDecimal guide = Commission.guideAmount(s.getTotal(), pct).subtract(Commission.guideAmount(after, pct));
                BigDecimal staff = commission.staffAmount(s.getTotal()).subtract(commission.staffAmount(after));
                UserAccount by = people.get(c.path("by").asString());
                creditNotes.save(new CreditNote(c.path("id").asString(), data.instant(c.path("date")), x, guide, staff,
                        by == null ? "Owner" : by.getName()));
            }
            n++;
        }
        return n;
    }
}
