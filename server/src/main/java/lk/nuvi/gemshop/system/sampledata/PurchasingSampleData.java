package lk.nuvi.gemshop.system.sampledata;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemRepository;
import lk.nuvi.gemshop.purchasing.Purchase;
import lk.nuvi.gemshop.purchasing.PurchaseRepository;
import lk.nuvi.gemshop.purchasing.Supplier;
import lk.nuvi.gemshop.purchasing.SupplierRepository;
import lk.nuvi.gemshop.purchasing.SupplierReturn;
import lk.nuvi.gemshop.purchasing.SupplierReturn.RefundMethod;
import lk.nuvi.gemshop.purchasing.SupplierReturnRepository;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** The prototype's 5 suppliers, 14 cash purchases (linked to the gems) and the return to supplier (P2-03). */
@Component
class PurchasingSampleData implements SampleDataPart {

    private final SupplierRepository suppliers;
    private final PurchaseRepository purchases;
    private final SupplierReturnRepository returns;
    private final GemRepository gems;
    private final BranchRepository branches;
    private final UserAccountRepository users;
    private final Numbers numbers;

    PurchasingSampleData(SupplierRepository suppliers, PurchaseRepository purchases, SupplierReturnRepository returns,
            GemRepository gems, BranchRepository branches, UserAccountRepository users, Numbers numbers) {
        this.suppliers = suppliers;
        this.purchases = purchases;
        this.returns = returns;
        this.gems = gems;
        this.branches = branches;
        this.users = users;
        this.numbers = numbers;
    }

    @Override
    public int order() {
        return 310;
    }

    @Override
    public String name() {
        return "suppliers and purchases";
    }

    @Override
    public int catchUp(SampleData data) {
        return suppliers.count() == 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        Map<String, UserAccount> people = new HashMap<>();
        for (JsonNode u : data.node("users").values()) {
            users.findByEmail(UserAccount.normalise(u.path("email").asString())).ifPresent(x -> people.put(u.path("id").asString(), x));
        }
        Map<String, Supplier> byCode = new HashMap<>();
        int n = 0;
        for (JsonNode s : data.node("suppliers").values()) {
            Supplier x = suppliers.save(new Supplier(s.path("id").asString(), new Supplier.Details(s.path("name").asString(),
                    blank(s.path("contact")), s.path("phone").asString(), s.path("address").asString(),
                    Supplier.IdType.valueOf(s.path("idType").asString()), s.path("idNo").asString(), blank(s.path("notes")))));
            byCode.put(x.getCode(), x);
            n++;
        }
        long lastPurchase = 0;
        for (JsonNode p : data.node("purchases").values()) {
            String number = p.path("id").asString();
            Purchase x = new Purchase(number, data.date(p.path("date")), supplier(byCode, p.path("supplierId").asString()),
                    branches.findByCode(p.path("branch").asString())
                            .orElseThrow(() -> new IllegalStateException("Sample data: unknown branch in " + number)),
                    null, blank(p.path("notes")), people.get(p.path("by").asString()));
            for (JsonNode l : p.path("lines").values()) {
                x.add(gem(l.path("productId").asString()), SampleData.decimal(l.path("cost")));
            }
            purchases.save(x);
            lastPurchase = Math.max(lastPurchase, seqOf(number));
            n++;
        }
        long lastReturn = 0;
        for (JsonNode r : data.node("supplierReturns").values()) {
            String number = r.path("id").asString();
            returns.save(new SupplierReturn(number, data.instant(r.path("date")), supplier(byCode, r.path("supplierId").asString()),
                    gem(r.path("productId").asString()), r.path("reason").asString(), SampleData.decimal(r.path("refund")),
                    method(r.path("refundMethod").asString("Cash")), people.get(r.path("by").asString())));
            lastReturn = Math.max(lastReturn, seqOf(number));
            n++;
        }
        JsonNode seq = data.node("seq");
        numbers.continueAfter(Counter.PURCHASE, Math.max(lastPurchase, seq.path("purchase").asLong(0)));
        numbers.continueAfter(Counter.SUPPLIER_RETURN, Math.max(lastReturn, seq.path("supplierReturn").asLong(0)));
        return n;
    }

    private static Supplier supplier(Map<String, Supplier> byCode, String code) {
        Supplier s = byCode.get(code);
        if (s == null) {
            throw new IllegalStateException("Sample data: unknown supplier '" + code + "'");
        }
        return s;
    }

    private Gem gem(String code) {
        return gems.findByCode(code).orElseThrow(() -> new IllegalStateException("Sample data: unknown gem '" + code + "'"));
    }

    static RefundMethod method(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        if (t.startsWith("replace")) {
            return RefundMethod.REPLACEMENT;
        }
        return t.startsWith("credit") ? RefundMethod.CREDIT : RefundMethod.CASH;
    }

    private static long seqOf(String number) {
        return Long.parseLong(number.substring(number.indexOf('-') + 1));
    }

    private static String blank(JsonNode n) {
        String s = SampleData.text(n);
        return s == null || s.isBlank() ? null : s;
    }
}
