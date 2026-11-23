package lk.nuvi.gemshop.reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.expenses.Expense;
import lk.nuvi.gemshop.expenses.ExpenseRepository;
import lk.nuvi.gemshop.files.FileService;
import lk.nuvi.gemshop.files.StoredFile;
import lk.nuvi.gemshop.files.pdf.PdfBody;
import lk.nuvi.gemshop.files.pdf.PdfDocuments.Line;
import lk.nuvi.gemshop.purchasing.Purchase;
import lk.nuvi.gemshop.purchasing.PurchaseRepository;
import lk.nuvi.gemshop.purchasing.Purchasing;
import lk.nuvi.gemshop.reports.Finance.Book;
import lk.nuvi.gemshop.reports.ReportFiles.Col;
import lk.nuvi.gemshop.reports.ReportFiles.Sheet;
import lk.nuvi.gemshop.reports.Reports.Period;
import lk.nuvi.gemshop.reports.Reports.SalesFilter;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.PlMonth;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.TaxPackPreview;
import lk.nuvi.gemshop.reports.api.ReportDtos.SalesBy;
import lk.nuvi.gemshop.sales.CreditNote;
import lk.nuvi.gemshop.sales.CreditNoteRepository;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tax pack (BRD N5): everything the accountant needs for any months, in one ZIP – each report as Excel and / or PDF, and
 * the bill photos of the expenses and purchases. The branch at the top filters (All branches = the whole shop).
 * <pre>
 * Tax pack 2026-08 to 2026-10.zip
 *   01 Sales/Sales by day.xlsx, Invoices.xlsx            05 Commissions/Commission report.xlsx
 *   02 Purchases/…, Bill photos/PUR-0012.jpg              06 Daily closings/Daily cash report.xlsx
 *   03 Expenses/Expenses.xlsx, Bill photos/EXP-0007.jpg   07 Credit notes/Credit notes.xlsx
 *   04 Profit and Loss/Profit and Loss.xlsx
 * </pre>
 */
@Service
public class TaxPack {

    public enum Part { SALES, PURCHASES, EXPENSES, PROFIT_LOSS, COMMISSIONS, CLOSINGS, CREDIT_NOTES }

    public enum Format { BOTH, XLSX, PDF }

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Finance finance;
    private final Reports reports;
    private final ReportSheets sheets;
    private final ReportFiles files;
    private final SaleRepository sales;
    private final PurchaseRepository purchases;
    private final ExpenseRepository expenses;
    private final CreditNoteRepository creditNotes;
    private final FileService fileService;
    private final ActivityLog log;
    private final CurrentUser current;

    public TaxPack(Finance finance, Reports reports, ReportSheets sheets, ReportFiles files, SaleRepository sales,
            PurchaseRepository purchases, ExpenseRepository expenses, CreditNoteRepository creditNotes, FileService fileService,
            ActivityLog log, CurrentUser current) {
        this.finance = finance;
        this.reports = reports;
        this.sheets = sheets;
        this.files = files;
        this.sales = sales;
        this.purchases = purchases;
        this.expenses = expenses;
        this.creditNotes = creditNotes;
        this.fileService = fileService;
        this.log = log;
        this.current = current;
    }

    /** The chosen months: from ≤ to (swapped if needed); empty = last month. */
    public record Months(YearMonth from, YearMonth to) {
        Period period() {
            return new Period(from.atDay(1), to.atEndOfMonth());
        }

        String name() {
            return from.equals(to) ? from.toString() : from + " to " + to;
        }
    }

    public Months months(String from, String to) {
        YearMonth last = finance.thisMonth().minusMonths(1);
        YearMonth f = from == null || from.isBlank() ? last : Finance.parse(from, "From");
        YearMonth t = to == null || to.isBlank() ? f : Finance.parse(to, "To");
        if (f.isAfter(t)) {
            YearMonth x = f;
            f = t;
            t = x;
        }
        if (t.isAfter(finance.thisMonth())) {
            throw ApiException.badRequest("BAD_MONTH", "The To month cannot be after this month.");
        }
        if (f.plusYears(5).isBefore(t)) {
            throw ApiException.badRequest("BAD_MONTH", "Please choose at most 5 years.");
        }
        return new Months(f, t);
    }

    @Transactional(readOnly = true)
    public TaxPackPreview preview(Months m) {
        Book book = finance.book(current.scope().branchId(), null, finance.thisMonth());
        List<PlMonth> in = inRange(book, m);
        return new TaxPackPreview(m.from().toString(), m.to().toString(), in.stream().mapToInt(PlMonth::invoices).sum(),
                sum(in, PlMonth::netSales), sum(in, PlMonth::netProfit), expenseBills(m).size() + purchaseBills(m).size(),
                book.months().stream().map(YearMonth::toString).toList());
    }

    public record Zip(String name, byte[] bytes, int files) {}

    /** Not read-only: it writes "Tax pack downloaded" to the activity log. */
    @Transactional
    public Zip build(Months m, Set<Part> parts, Format format, boolean photos) {
        if (parts.isEmpty()) {
            throw ApiException.badRequest("NO_PARTS", "Choose at least one part of the tax pack.");
        }
        String root = "Tax pack " + m.name() + "/";
        Period p = m.period();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n = 0;
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            if (parts.contains(Part.SALES)) {
                n += put(zip, root + "01 Sales/", format, sheets.sales(reports.sales(p, new SalesFilter(null, null, null, null, SalesBy.DAY))));
                n += put(zip, root + "01 Sales/", format, invoices(p));
            }
            if (parts.contains(Part.PURCHASES)) {
                n += put(zip, root + "02 Purchases/", format, sheets.purchases(reports.purchases(p)));
                n += put(zip, root + "02 Purchases/", format, purchaseList(m));
                if (photos) {
                    n += photos(zip, root + "02 Purchases/Bill photos/", purchaseBills(m));
                }
            }
            if (parts.contains(Part.EXPENSES)) {
                n += put(zip, root + "03 Expenses/", format, expenseList(m));
                if (photos) {
                    n += photos(zip, root + "03 Expenses/Bill photos/", expenseBills(m));
                }
            }
            if (parts.contains(Part.PROFIT_LOSS)) {
                n += put(zip, root + "04 Profit and Loss/", format, profitLoss(m));
            }
            if (parts.contains(Part.COMMISSIONS)) {
                n += put(zip, root + "05 Commissions/", format, sheets.commissions(reports.commissions(p, null)));
            }
            if (parts.contains(Part.CLOSINGS)) {
                n += put(zip, root + "06 Daily closings/", format, sheets.dailyCash(reports.dailyCash(p, false)));
            }
            if (parts.contains(Part.CREDIT_NOTES)) {
                n += put(zip, root + "07 Credit notes/", format, creditNoteList(p));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.add("Tax pack downloaded", m.name() + " · " + parts.size() + " part(s)" + (photos ? " + bill photos" : "") + " · "
                + sheets.branchName(), current.scope().branchId());
        return new Zip("Tax pack " + m.name(), out.toByteArray(), n);
    }

    public static Set<Part> parts(List<String> names) {
        if (names == null || names.isEmpty()) {
            return EnumSet.allOf(Part.class);
        }
        Set<Part> s = EnumSet.noneOf(Part.class);
        for (String x : names) {
            for (String one : x.split(",")) {
                if (!one.isBlank()) {
                    try {
                        s.add(Part.valueOf(one.trim().toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        throw ApiException.badRequest("BAD_PART", "Unknown part of the tax pack: " + one);
                    }
                }
            }
        }
        return s;
    }

    // ------------------------------------------------------------------ sheets

    private Sheet invoices(Period p) {
        var from = p.from().atStartOfDay(finance.zone()).toInstant();
        var to = p.to().plusDays(1).atStartOfDay(finance.zone()).toInstant();
        List<Sale> list = new ArrayList<>(sales.search(current.scope().branchId(), from, to, null));
        list.sort(java.util.Comparator.comparing(Sale::getSoldAt));
        List<Object[]> rows = list.stream().map(s -> new Object[] {s.getNumber(), s.getSoldAt(), s.getBranch().getDisplayName(),
                s.getCustomer().getName(), s.getCustomer().getCountry(), s.getItems().size(), s.getTotal(), s.getCardFee(),
                s.getStatus() == Sale.Status.CANCELLED ? "Cancelled" : "Completed"}).toList();
        BigDecimal total = list.stream().filter(s -> s.getStatus() == Sale.Status.COMPLETED).map(Sale::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Sheet("INVOICES", "Invoices", head(p), List.of(Col.text("Invoice", 14), Col.dateTime("Date", 17), Col.text("Branch", 14),
                Col.text("Customer", 24), Col.text("Country", 14), Col.number("Gems", 7), Col.money("Total", 15), Col.money("Card bank fee", 14),
                Col.text("Status", 12)), rows, new Object[] {"Total (not cancelled)", null, null, null, null, null, total, null, null},
                "Cancelled invoices are listed but not counted.", true);
    }

    private Sheet purchaseList(Months m) {
        List<Purchase> list = purchaseRows(m);
        Set<String> bills = new HashSet<>();
        purchaseBills(m).forEach(f -> bills.add(f.getOwnerRef()));
        List<Object[]> rows = list.stream().map(x -> new Object[] {x.getNumber(), x.getDate(), x.getSupplier().getName(),
                x.getBranch().getDisplayName(), x.getLines().size(), x.getTotal(), x.getPaidBy(), bills.contains(x.getNumber()) ? "Yes" : "No"})
                .toList();
        return new Sheet("PURCHASES", "Purchases", head(m.period()), List.of(Col.text("Purchase", 14), Col.date("Date", 12),
                Col.text("Supplier", 26), Col.text("Branch", 14), Col.number("Gems", 7), Col.money("Total", 16), Col.text("Paid by", 12),
                Col.text("Bill photo", 10)), rows, new Object[] {"Total", null, null, null, null,
                list.stream().map(Purchase::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add), null, null}, null, true);
    }

    private Sheet expenseList(Months m) {
        List<Expense> list = expenseRows(m);
        Set<String> bills = new HashSet<>();
        expenseBills(m).forEach(f -> bills.add(f.getOwnerRef()));
        List<Object[]> rows = list.stream().map(e -> new Object[] {e.getNumber(), e.getSpentAt(), e.getBranch().getDisplayName(),
                e.getExpenseType(), e.getPaidTo(), method(e.getMethod()), e.getAmount(), status(e.getStatus()),
                bills.contains(e.getNumber()) ? "Yes" : e.getSampleBill() != null ? "Sample" : "No"}).toList();
        BigDecimal approved = list.stream().filter(e -> e.getStatus() == Expense.Status.APPROVED).map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Sheet("EXPENSES", "Expenses", head(m.period()), List.of(Col.text("Expense", 12), Col.date("Date", 12),
                Col.text("Branch", 14), Col.text("Expense type", 20), Col.text("Paid to", 24), Col.text("Method", 14), Col.money("Amount", 15),
                Col.text("Status", 18), Col.text("Bill photo", 10)), rows,
                new Object[] {"Total approved", null, null, null, null, null, approved, null, null}, "Only approved expenses count in the P&L.",
                true);
    }

    private Sheet profitLoss(Months m) {
        Book book = finance.book(current.scope().branchId(), m.from(), m.to());
        List<PlMonth> in = new ArrayList<>(inRange(book, m));
        in.sort(java.util.Comparator.comparing(PlMonth::month));
        List<Object[]> rows = in.stream().map(x -> new Object[] {x.month(), x.invoices(), x.sales(), x.returns(), x.netSales(), x.cost(),
                x.grossProfit(), x.commissions(), x.cardFees(), x.expenses(), x.netProfit(), x.closed() ? "Closed" : "Open"}).toList();
        List<Line> lines = new ArrayList<>(head(m.period()));
        lines.add(new Line("Net profit", "LKR " + PdfBody.money(sum(in, PlMonth::netProfit))));
        return new Sheet("PROFIT AND LOSS", "Profit and Loss", lines, List.of(Col.text("Month", 10), Col.number("Invoices", 9),
                Col.money("Sales", 14), Col.money("Returns", 13), Col.money("Net sales", 14), Col.money("Cost", 14), Col.money("Gross profit", 14),
                Col.money("Commissions", 13), Col.money("Card fees", 12), Col.money("Expenses", 13), Col.money("Net profit", 14),
                Col.text("Status", 8)), rows, new Object[] {"Total", in.stream().mapToInt(PlMonth::invoices).sum(), sum(in, PlMonth::sales),
                sum(in, PlMonth::returns), sum(in, PlMonth::netSales), sum(in, PlMonth::cost), sum(in, PlMonth::grossProfit),
                sum(in, PlMonth::commissions), sum(in, PlMonth::cardFees), sum(in, PlMonth::expenses), sum(in, PlMonth::netProfit), null},
                "Net profit = sales − returns − cost − commissions − card bank fees − expenses.", true);
    }

    private Sheet creditNoteList(Period p) {
        Long branch = current.scope().branchId();
        List<CreditNote> list = creditNotes.list(branch).stream()
                .filter(c -> {
                    var d = c.getIssuedAt().atZone(finance.zone()).toLocalDate();
                    return !d.isBefore(p.from()) && !d.isAfter(p.to());
                })
                .sorted(java.util.Comparator.comparing(CreditNote::getIssuedAt)).toList();
        List<Object[]> rows = list.stream().map(c -> new Object[] {c.getNumber(), c.getIssuedAt(), c.getSaleReturn().getSale().getNumber(),
                c.getSaleReturn().getKind() == lk.nuvi.gemshop.sales.SaleReturn.Kind.EXCHANGE ? "Exchange" : "Refund",
                c.getSaleReturn().getGem().getCode(), c.getAmount(),
                c.getUsedSaleNo() != null ? "Used on " + c.getUsedSaleNo()
                        : c.getCashRefundAt() != null ? "Paid back " + DMY.format(c.getCashRefundAt().atZone(finance.zone())) : "Open"})
                .toList();
        return new Sheet("CREDIT NOTES", "Credit notes", head(p), List.of(Col.text("Credit note", 14), Col.dateTime("Date", 17),
                Col.text("Invoice", 14), Col.text("Kind", 10), Col.text("Gem", 12), Col.money("Amount", 15), Col.text("Used / paid", 26)),
                rows, new Object[] {"Total", null, null, null, null, list.stream().map(CreditNote::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                null}, null, false);
    }

    // ------------------------------------------------------------------ helpers

    private List<Purchase> purchaseRows(Months m) {
        List<Purchase> list = new ArrayList<>(purchases.search(null, current.scope().branchId(), m.period().from(), m.period().to()));
        list.sort(java.util.Comparator.comparing(Purchase::getDate).thenComparing(Purchase::getNumber));
        return list;
    }

    private List<Expense> expenseRows(Months m) {
        Period p = m.period();
        return expenses.all(current.scope().branchId()).stream()
                .filter(e -> {
                    var d = e.getSpentAt().atZone(finance.zone()).toLocalDate();
                    return !d.isBefore(p.from()) && !d.isAfter(p.to());
                })
                .sorted(java.util.Comparator.comparing(Expense::getSpentAt)).toList();
    }

    private List<StoredFile> expenseBills(Months m) {
        List<String> nos = expenseRows(m).stream().map(Expense::getNumber).toList();
        return nos.isEmpty() ? List.of() : fileService.ofOwners(Expense.OWNER_TYPE, nos);
    }

    private List<StoredFile> purchaseBills(Months m) {
        List<String> nos = purchaseRows(m).stream().map(Purchase::getNumber).toList();
        return nos.isEmpty() ? List.of() : fileService.ofOwners(Purchasing.OWNER_TYPE, nos);
    }

    private int put(ZipOutputStream zip, String folder, Format f, Sheet s) throws IOException {
        int n = 0;
        if (f != Format.PDF) {
            entry(zip, folder + s.name() + ".xlsx", files.xlsx(s));
            n++;
        }
        if (f != Format.XLSX) {
            entry(zip, folder + s.name() + ".pdf", files.pdf(s, finance.zone()));
            n++;
        }
        return n;
    }

    private int photos(ZipOutputStream zip, String folder, List<StoredFile> list) throws IOException {
        Map<String, Integer> seen = new HashMap<>();
        int n = 0;
        for (StoredFile f : list) {
            var bytes = fileService.bytes(f);
            if (bytes.isEmpty()) {
                continue;
            }
            int k = seen.merge(f.getOwnerRef(), 1, Integer::sum);
            entry(zip, folder + f.getOwnerRef() + (k > 1 ? " (" + k + ")" : "") + extension(f), bytes.get());
            n++;
        }
        return n;
    }

    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String extension(StoredFile f) {
        String t = f.getContentType() == null ? "" : f.getContentType();
        return switch (t) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> {
                String n = f.getOriginalName();
                int dot = n == null ? -1 : n.lastIndexOf('.');
                yield dot < 0 ? "" : n.substring(dot);
            }
        };
    }

    private List<Line> head(Period p) {
        return List.of(new Line("Period", DMY.format(p.from()) + " – " + DMY.format(p.to())), new Line("Branch", sheets.branchName()));
    }

    private static List<PlMonth> inRange(Book book, Months m) {
        List<PlMonth> out = new ArrayList<>();
        for (YearMonth x = m.from(); !x.isAfter(m.to()); x = x.plusMonths(1)) {
            out.add(book.month(x));
        }
        return out;
    }

    private static String method(Expense.Method m) {
        return switch (m) {
            case DRAWER -> "Drawer cash";
            case OWNER_CASH -> "Owner's cash";
            case BANK -> "Bank transfer";
            case CARD -> "Card";
        };
    }

    private static String status(Expense.Status s) {
        return switch (s) {
            case APPROVED -> "Approved";
            case PENDING -> "Waiting for owner";
            case REJECTED -> "Rejected";
        };
    }

    private static <T> BigDecimal sum(List<T> rows, java.util.function.Function<T, BigDecimal> f) {
        return rows.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
