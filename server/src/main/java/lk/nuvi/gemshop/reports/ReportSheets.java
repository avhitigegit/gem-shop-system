package lk.nuvi.gemshop.reports;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ExpenseReport;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ReportMonth;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ReportTypeRow;
import lk.nuvi.gemshop.files.pdf.PdfBody;
import lk.nuvi.gemshop.files.pdf.PdfDocuments.Line;
import lk.nuvi.gemshop.reports.ReportFiles.Col;
import lk.nuvi.gemshop.reports.ReportFiles.Sheet;
import lk.nuvi.gemshop.reports.api.ReportDtos.CashReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.CashReportRow;
import lk.nuvi.gemshop.reports.api.ReportDtos.CommissionPersonRow;
import lk.nuvi.gemshop.reports.api.ReportDtos.CommissionReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.ProfitReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.ProfitRow;
import lk.nuvi.gemshop.reports.api.ReportDtos.PurchaseReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.SalesBy;
import lk.nuvi.gemshop.reports.api.ReportDtos.SalesReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.StockReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.StockReportRow;
import lk.nuvi.gemshop.reports.api.ReportDtos.SupplierReportRow;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Turns each report into the table of its PDF / Excel download (same columns as the screen). */
@Component
public class ReportSheets {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final Map<SalesBy, String> BY = Map.of(SalesBy.DAY, "Day", SalesBy.MONTH, "Month", SalesBy.BRANCH, "Branch",
            SalesBy.GEM, "Gem type", SalesBy.STAFF, "Salesperson", SalesBy.GUIDE, "Guide / driver", SalesBy.PAY, "Payment method",
            SalesBy.COUNTRY, "Customer country");
    private static final Map<String, String> EXPENSE_METHOD = Map.of("DRAWER", "Drawer cash", "OWNER_CASH", "Owner's cash",
            "BANK", "Bank transfer", "CARD", "Card");

    private final CurrentUser current;
    private final BranchRepository branches;

    public ReportSheets(CurrentUser current, BranchRepository branches) {
        this.current = current;
        this.branches = branches;
    }

    public Sheet sales(SalesReport r) {
        List<Object[]> rows = r.rows().stream().map(x -> new Object[] {x.label(), x.bills(), x.items(), x.total()}).toList();
        return new Sheet("SALES REPORT", "Sales report", head(r.from(), r.to(), List.of(new Line("Grouped by", BY.get(r.by())),
                new Line("Sales", "LKR " + PdfBody.money(r.total())), new Line("Bills", String.valueOf(r.bills())),
                new Line("Average bill", "LKR " + PdfBody.money(r.averageBill())))),
                List.of(Col.text(BY.get(r.by()), 30), Col.number("Bills", 10), Col.number("Items", 10), Col.money("Total (LKR)", 18)),
                rows, new Object[] {"Total", r.bills(), r.items(), r.total()}, "Cancelled invoices and returned gems are not included.",
                false);
    }

    public Sheet profit(ProfitReport r) {
        List<Object[]> rows = r.rows().stream().map((ProfitRow x) -> new Object[] {x.gem(), x.name(), x.gemType(), x.soldAt(),
                x.invoice(), x.price(), x.cost(), x.commission(), x.profit(), x.margin()}).toList();
        return new Sheet("PROFIT REPORT", "Profit report", head(r.from(), r.to(), List.of(
                new Line("Sales", "LKR " + PdfBody.money(r.sales())), new Line("Gross profit", "LKR " + PdfBody.money(r.profit())),
                new Line("Margin", r.margin().toPlainString() + "%"))),
                List.of(Col.text("Gem", 12), Col.text("Name", 26), Col.text("Gem type", 16), Col.date("Sold", 12), Col.text("Invoice", 14),
                        Col.money("Selling price", 15), Col.money("Cost", 15), Col.money("Commission", 14), Col.money("Profit", 15),
                        Col.number("Margin %", 10)),
                rows, new Object[] {"Total", null, null, null, null, r.sales(), r.cost(), r.commission(), r.profit(), r.margin()},
                "Commissions are shared across the items of a bill by price. Shop expenses are in the Profit & Loss.", true);
    }

    public Sheet dailyCash(CashReport r) {
        List<Object[]> rows = r.rows().stream().map((CashReportRow x) -> new Object[] {x.day(), x.branch(), x.closedBy(),
                x.expected().lkr(), x.counted().lkr(), x.expected().usd(), x.counted().usd(), x.expected().eur(), x.counted().eur(),
                x.expected().card(), x.counted().card(), x.difference(), x.balanced() ? "Balanced" : "Difference", x.note()}).toList();
        return new Sheet("DAILY CASH REPORT", "Daily cash report", head(r.from(), r.to(), List.of(
                new Line("Days closed", String.valueOf(r.days())), new Line("Days with a difference", String.valueOf(r.daysWithDifference())),
                new Line("Net difference", "LKR " + PdfBody.money(r.netDifference())))),
                List.of(Col.date("Date", 12), Col.text("Branch", 14), Col.text("Closed by", 16), Col.money("LKR expected", 14),
                        Col.money("LKR counted", 14), Col.money("USD expected", 12), Col.money("USD counted", 12), Col.money("EUR expected", 12),
                        Col.money("EUR counted", 12), Col.money("Card expected", 14), Col.money("Card counted", 14),
                        Col.money("Difference (LKR)", 15), Col.text("Result", 12), Col.text("Note", 30)),
                rows, new Object[] {"Total", null, null, null, null, null, null, null, null, null, null, r.netDifference(), null, null},
                "Difference = counted − expected, all currencies in LKR at the day's rates.", true);
    }

    public Sheet stock(StockReport r) {
        Map<String, String> names = new java.util.HashMap<>();
        branches.findAll().forEach(b -> names.put(b.getCode(), b.getDisplayName()));
        List<Object[]> rows = r.rows().stream().map((StockReportRow x) -> new Object[] {x.code(), x.name(), x.gemType(), x.productType(),
                x.carat(), names.getOrDefault(x.branch(), x.branch()), x.status().label(), x.addedAt(), x.days(), x.cost(), x.price()}).toList();
        List<Line> extra = new ArrayList<>();
        if (r.slow()) {
            extra.add(new Line("Slow-moving", "not sold for more than " + r.slowDays() + " days"));
        }
        extra.add(new Line("Items", String.valueOf(r.items())));
        extra.add(new Line("Value at cost", "LKR " + PdfBody.money(r.cost())));
        extra.add(new Line("Value at selling price", "LKR " + PdfBody.money(r.price())));
        return new Sheet(r.slow() ? "SLOW-MOVING STOCK" : "STOCK REPORT", r.slow() ? "Slow-moving stock" : "Stock report",
                head(null, null, extra),
                List.of(Col.text("Code", 12), Col.text("Name", 26), Col.text("Gem type", 16), Col.text("Type", 14), Col.number("Carat", 8),
                        Col.text("Branch", 14), Col.text("Status", 14), Col.date("In stock since", 13), Col.number("Days", 8),
                        Col.money("Cost", 15), Col.money("Selling price", 15)),
                rows, new Object[] {r.items() + " items", null, null, null, null, null, null, null, null, r.cost(), r.price()},
                "Cost prices – owner only.", true);
    }

    public Sheet commissions(CommissionReport r) {
        List<Object[]> rows = r.rows().stream().map((CommissionPersonRow x) -> new Object[] {x.name(), x.detail(), x.sales(),
                x.salesAmount(), x.earned(), x.reversed(), x.paid(), x.unpaid()}).toList();
        return new Sheet("COMMISSION REPORT", "Commission report", head(r.from(), r.to(), List.of(
                new Line("Earned", "LKR " + PdfBody.money(r.earned())), new Line("Paid", "LKR " + PdfBody.money(r.paid())),
                new Line("Not yet paid", "LKR " + PdfBody.money(r.unpaid())))),
                List.of(Col.text("Name", 24), Col.text("Guide / staff", 30), Col.number("Sales", 8), Col.money("Sales amount", 16),
                        Col.money("Earned", 14), Col.money("Reversed", 14), Col.money("Paid", 14), Col.money("Not yet paid", 14)),
                rows, new Object[] {"Total", null, r.sales(), r.salesAmount(), r.earned(), r.reversed(), r.paid(), r.unpaid()},
                "Reversed = commission taken back after a return or a cancelled invoice.", true);
    }

    public Sheet purchases(PurchaseReport r) {
        List<Object[]> rows = r.rows().stream().map((SupplierReportRow x) -> new Object[] {x.code(), x.name(), x.address(), x.purchases(),
                x.products(), x.sold(), x.cost(), x.returned(), x.lastPurchase()}).toList();
        return new Sheet("PURCHASES BY SUPPLIER", "Purchases by supplier", head(r.from(), r.to(), List.of(
                new Line("Total bought", "LKR " + PdfBody.money(r.cost())), new Line("Products", r.products() + " (" + r.sold() + " sold)"))),
                List.of(Col.text("Code", 10), Col.text("Supplier", 24), Col.text("Address", 28), Col.number("Purchases", 10),
                        Col.number("Products", 10), Col.number("Sold", 8), Col.money("Total cost", 16), Col.money("Returned", 14),
                        Col.date("Last purchase", 13)),
                rows, new Object[] {"Total", null, null, r.purchases(), r.products(), r.sold(), r.cost(), r.returned(), null}, null, true);
    }

    public Sheet expenses(ExpenseReport r) {
        List<Col> cols = new ArrayList<>();
        cols.add(Col.text("Expense type", 24));
        for (ReportMonth m : r.months()) {
            cols.add(Col.money(monthLabel(m.month()), 14));
        }
        cols.add(Col.money("Total", 16));
        List<Object[]> rows = new ArrayList<>();
        for (ReportTypeRow t : r.types()) {
            List<Object> row = new ArrayList<>();
            row.add(t.expenseType());
            row.addAll(t.amounts());
            row.add(t.total());
            rows.add(row.toArray());
        }
        List<Object> totals = new ArrayList<>();
        totals.add("Total");
        r.months().forEach(m -> totals.add(m.total()));
        totals.add(r.total());
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Branch", branchName()));
        lines.add(new Line("Total", "LKR " + PdfBody.money(r.total())));
        r.methods().forEach(m -> lines.add(new Line(EXPENSE_METHOD.getOrDefault(m.method(), m.method()), "LKR " + PdfBody.money(m.total()))));
        return new Sheet("EXPENSE REPORT", "Expense report", lines, cols, rows, totals.toArray(), "Approved expenses only.",
                r.months().size() > 4);
    }

    // ------------------------------------------------------------------ helpers

    private List<Line> head(LocalDate from, LocalDate to, List<Line> more) {
        List<Line> l = new ArrayList<>();
        if (from != null) {
            l.add(new Line("Period", DMY.format(from) + " – " + DMY.format(to)));
        }
        l.add(new Line("Branch", branchName()));
        l.addAll(more);
        return l;
    }

    @Transactional(readOnly = true)
    String branchName() {
        Long id = current.scope().branchId();
        return id == null ? "All branches" : branches.findById(id).map(Branch::getDisplayName).orElse("—");
    }

    private static String monthLabel(String key) {
        LocalDate d = LocalDate.parse(key + "-01");
        return d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) + " " + d.getYear();
    }
}
