package lk.nuvi.gemshop.system.sampledata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.expenses.Expense;
import lk.nuvi.gemshop.expenses.ExpenseRepository;
import lk.nuvi.gemshop.expenses.MonthlyBill;
import lk.nuvi.gemshop.expenses.MonthlyBillRepository;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The prototype's 29 expenses (bank, card and drawer; one waiting for the owner) and its 3 monthly bills. The bills are
 * only file names in the prototype, so they are kept as "sample bill" (no real photo). Runs after the approvals (950) to
 * link the waiting expense to its request.
 */
@Component
class ExpensesSampleData implements SampleDataPart {

    private final ExpenseRepository expenses;
    private final MonthlyBillRepository bills;
    private final BranchRepository branches;
    private final UserAccountRepository users;
    private final JdbcTemplate jdbc;

    ExpensesSampleData(ExpenseRepository expenses, MonthlyBillRepository bills, BranchRepository branches, UserAccountRepository users,
            JdbcTemplate jdbc) {
        this.expenses = expenses;
        this.bills = bills;
        this.branches = branches;
        this.users = users;
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 960;
    }

    @Override
    public String name() {
        return "expenses and monthly bills";
    }

    @Override
    public int catchUp(SampleData data) {
        // the sample numbers EXP-0001…0029 are kept free by the number counter, so a database with newer expenses still gets them
        return bills.count() == 0 && expenses.findByNumber("EXP-0001").isEmpty() && branches.count() > 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        Map<String, UserAccount> people = new HashMap<>();
        for (JsonNode u : data.node("users").values()) {
            users.findByEmail(UserAccount.normalise(u.path("email").asString())).ifPresent(x -> people.put(u.path("id").asString(), x));
        }
        Map<String, MonthlyBill> byId = new HashMap<>();
        int n = 0;
        for (JsonNode r : data.node("recurring").values()) {
            Branch b = branches.findByCode(r.path("branch").asString()).orElse(null);
            if (b == null) {
                continue;
            }
            MonthlyBill bill = new MonthlyBill(r.path("id").asString(), b, r.path("category").asString(), SampleData.decimal(r.path("amount")),
                    r.path("paidTo").asString(), method(r.path("method").asString()), r.path("day").asInt(1));
            bill.active(r.path("active").asBoolean(true));
            bill.added(data.date(r.path("lastRun")));
            byId.put(r.path("id").asString(), bills.save(bill));
            n++;
        }
        for (JsonNode x : data.node("expenses").values()) {
            Branch b = branches.findByCode(x.path("branch").asString()).orElse(null);
            Instant at = data.instant(x.path("date"));
            if (b == null || at == null) {
                continue;
            }
            BigDecimal amount = SampleData.decimal(x.path("amount"));
            String status = x.path("status").asString("approved").toUpperCase(java.util.Locale.ROOT);
            Expense e = new Expense(x.path("id").asString(), at, b, x.path("category").asString(), amount, x.path("paidTo").asString(),
                    method(x.path("method").asString()), Expense.Status.valueOf(status), SampleData.text(x.path("note")),
                    people.get(x.path("by").asString()));
            String bill = SampleData.text(x.path("billPhoto"));
            if (bill != null) {
                e.sampleBill(bill);
            }
            String rc = SampleData.text(x.path("recurringId"));
            if (rc != null && byId.containsKey(rc)) {
                e.fromMonthlyBill(byId.get(rc));
            }
            expenses.save(e);
            n++;
        }
        expenses.flush();
        // the waiting expense ↔ its approval request (loaded by the approvals part)
        jdbc.update("""
                update expense e set approval_no = a.number
                from approval a where a.type = 'EXPENSE' and a.ref = e.number and e.approval_no is null""");
        return n;
    }

    private static Expense.Method method(String prototype) {
        String m = prototype == null ? "" : prototype.toLowerCase(java.util.Locale.ROOT);
        return m.contains("drawer") ? Expense.Method.DRAWER : m.contains("card") ? Expense.Method.CARD
                : m.contains("cash") ? Expense.Method.OWNER_CASH : Expense.Method.BANK;
    }
}
