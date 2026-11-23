package lk.nuvi.gemshop.dailycash;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lk.nuvi.gemshop.audit.alert.AlertType;
import lk.nuvi.gemshop.audit.alert.Alerts;
import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.audit.approval.Approvals;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.CurrentUser.Who;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.commissions.CommissionPayment;
import lk.nuvi.gemshop.commissions.CommissionPaymentRepository;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.mail.MailService;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.dailycash.api.CashDtos.CashDay;
import lk.nuvi.gemshop.dailycash.api.CashDtos.CashSummary;
import lk.nuvi.gemshop.dailycash.api.CashDtos.CloseDayForm;
import lk.nuvi.gemshop.dailycash.api.CashDtos.ClosingView;
import lk.nuvi.gemshop.dailycash.api.CashDtos.DayPayment;
import lk.nuvi.gemshop.dailycash.api.CashDtos.DrawerAmounts;
import lk.nuvi.gemshop.dailycash.api.CashDtos.DrawerCommissionRow;
import lk.nuvi.gemshop.dailycash.api.CashDtos.DrawerExpenseForm;
import lk.nuvi.gemshop.dailycash.api.CashDtos.DrawerExpenseRow;
import lk.nuvi.gemshop.dailycash.api.CashDtos.DrawerRefundRow;
import lk.nuvi.gemshop.dailycash.api.CashDtos.ExpectedCash;
import lk.nuvi.gemshop.dailycash.api.CashDtos.OpeningForm;
import lk.nuvi.gemshop.dailycash.api.CashDtos.OpeningRow;
import lk.nuvi.gemshop.dailycash.api.CashDtos.OpeningView;
import lk.nuvi.gemshop.expenses.Expense;
import lk.nuvi.gemshop.expenses.ExpenseRepository;
import lk.nuvi.gemshop.files.FileKind;
import lk.nuvi.gemshop.files.FileService;
import lk.nuvi.gemshop.files.StoredFile;
import lk.nuvi.gemshop.sales.CreditNote;
import lk.nuvi.gemshop.sales.CreditNoteRepository;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.SalePayment;
import lk.nuvi.gemshop.sales.SaleRepository;
import lk.nuvi.gemshop.sales.Sales;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.settings.branch.BranchStatus;
import lk.nuvi.gemshop.settings.lists.ListName;
import lk.nuvi.gemshop.settings.lists.Lists;
import lk.nuvi.gemshop.settings.rates.Rates;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The cash drawer from morning to evening (BRD I1–I4, M4, M6; rule 11).
 * <pre>
 * expected cash  = opening + cash sales (per currency; LKR after change given) − cash paid out of the drawer
 *                  (expenses, refunds, guide commission "Cash (drawer)")
 * card expected  = card sales (the machine total – no fee added); the bank fee is a shop cost
 * difference     = counted − expected, all in LKR at the day's rates, card included
 * </pre>
 * Closing the day locks it ({@link DayLock}) and sends the closing report to the owner.
 */
@Service
public class DailyCash {

    public static final String NEED_BRANCH = "CHOOSE_BRANCH";
    public static final String NOTE_NEEDED = "NOTE_NEEDED";
    public static final String NOT_A_REFUND = "NOT_A_REFUND";
    public static final String CLOSE_LINK = "/money/close-day";
    public static final BigDecimal TOLERANCE = new BigDecimal("0.50");

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    private final CashOpeningRepository openings;
    private final DayClosingRepository closings;
    private final DayLock lock;
    private final SaleRepository sales;
    private final ExpenseRepository expenses;
    private final CommissionPaymentRepository commissionPayments;
    private final CreditNoteRepository creditNotes;
    private final BranchRepository branches;
    private final UserAccountRepository users;
    private final ShopSettingsRepository shops;
    private final Rates rates;
    private final Lists lists;
    private final FileService files;
    private final Approvals approvals;
    private final Numbers numbers;
    private final ActivityLog log;
    private final Alerts alerts;
    private final MailService mail;
    private final ClosingReport report;
    private final CurrentUser current;
    private final ZoneId zone;

    public DailyCash(CashOpeningRepository openings, DayClosingRepository closings, DayLock lock, SaleRepository sales,
            ExpenseRepository expenses, CommissionPaymentRepository commissionPayments, CreditNoteRepository creditNotes,
            BranchRepository branches, UserAccountRepository users, ShopSettingsRepository shops, Rates rates, Lists lists, FileService files,
            Approvals approvals, Numbers numbers, ActivityLog log, Alerts alerts, MailService mail, ClosingReport report, CurrentUser current,
            AppProperties app) {
        this.openings = openings;
        this.closings = closings;
        this.lock = lock;
        this.sales = sales;
        this.expenses = expenses;
        this.commissionPayments = commissionPayments;
        this.creditNotes = creditNotes;
        this.branches = branches;
        this.users = users;
        this.shops = shops;
        this.rates = rates;
        this.lists = lists;
        this.files = files;
        this.approvals = approvals;
        this.numbers = numbers;
        this.log = log;
        this.alerts = alerts;
        this.mail = mail;
        this.report = report;
        this.current = current;
        this.zone = ZoneId.of(app.timezone());
    }

    // ------------------------------------------------------------------ the day

    @Transactional(readOnly = true)
    public CashSummary summary() {
        Long scope = current.scope().branchId();
        Branch b = drawerBranchOrNull();
        LocalDate today = today();
        int notMatching = (int) closings.since(scope, today.minusDays(7)).stream()
                .filter(c -> c.getDifference().abs().compareTo(BigDecimal.ONE) > 0).count();
        return new CashSummary(b == null ? null : b.getCode(), b != null && openings.findByBranchIdAndDay(b.getId(), today).isPresent(),
                b != null && lock.isClosed(b.getId(), today), expenses.countWaiting(scope), notMatching);
    }

    @Transactional(readOnly = true)
    public CashDay day() {
        Branch b = drawerBranch();
        LocalDate d = today();
        return day(b, d);
    }

    /** Start the day: the cash in the drawer per currency (I1). Can be corrected until the day is closed. */
    @Transactional
    public CashDay open(OpeningForm f) {
        Who w = current.require();
        Branch b = drawerBranch();
        LocalDate d = today();
        lock.checkOpen(b.getId(), d);
        CashOpening o = openings.findByBranchIdAndDay(b.getId(), d).orElseGet(() -> new CashOpening(b, d));
        boolean first = o.getId() == null;
        o.set(new Money3(f.lkr(), f.usd(), f.eur()).scaled(), Instant.now(), users.getReferenceById(w.id()));
        openings.save(o);
        log.add(first ? "Opening cash entered" : "Opening cash corrected", "LKR " + num(f.lkr()) + " · USD " + num(f.usd()) + " · EUR "
                + num(f.eur()), b.getId());
        return day(b, d);
    }

    /** A small shop expense paid in cash from the drawer, with its bill photo (I2, M4, rule 11). Above the limit → owner (M6). */
    @Transactional
    public DrawerExpenseRow payExpense(DrawerExpenseForm f) {
        Who w = current.require();
        Branch b = drawerBranch();
        lock.checkOpen(b.getId(), today());
        String type = lists.check(ListName.EXPENSE_TYPE, f.expenseType(), "expenseType");
        BigDecimal amount = f.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal limit = shops.current().map(ShopSettings::getLargeExpenseLimit).orElse(new BigDecimal("50000"));
        boolean big = amount.compareTo(limit) > 0;
        String no = numbers.next(Counter.EXPENSE);
        Expense e = expenses.save(new Expense(no, Instant.now(), b, type, amount, f.paidTo().trim(), Expense.Method.DRAWER,
                big ? Expense.Status.PENDING : Expense.Status.APPROVED, blank(f.note()), users.getReferenceById(w.id())));
        files.attach(List.of(f.bill()), FileKind.BILL_PHOTO, Expense.OWNER_TYPE, no);
        if (big) {
            Approval a = approvals.request(new Approvals.Request(ApprovalType.EXPENSE, b.getId(), "Large expense: " + type + " " + Sales.lkr(amount),
                    "Paid to " + e.getPaidTo() + " from the drawer (limit " + Sales.lkr(limit) + ")", amount, no));
            e.asked(a.getNumber());
        }
        log.add("Drawer expense", no + " · " + type + " · " + Sales.lkr(amount) + (big ? " · waiting for the owner" : ""), b.getId());
        return row(e, bills(List.of(e)));
    }

    /** A refund credit note paid back in cash from the drawer (expected cash − refunds). */
    @Transactional
    public CashDay payRefund(String creditNote) {
        Who w = current.require();
        Branch b = drawerBranch();
        LocalDate d = today();
        lock.checkOpen(b.getId(), d);
        CreditNote c = creditNotes.findByNumber(creditNote == null ? "" : creditNote.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("No credit note " + creditNote + "."));
        if (!c.getSaleReturn().getBranch().getId().equals(b.getId())) {
            throw ApiException.forbidden("This credit note belongs to another branch.");
        }
        if (c.getSaleReturn().getKind() != lk.nuvi.gemshop.sales.SaleReturn.Kind.REFUND || c.isRefundedFromDrawer() || c.isUsed()) {
            throw ApiException.conflict(NOT_A_REFUND, c.getNumber() + " is not a refund waiting to be paid.");
        }
        c.refundedFromDrawer(Instant.now(), b, users.getReferenceById(w.id()));
        log.add("Refund paid from drawer", c.getNumber() + " · " + c.getSaleReturn().getSale().getCustomer().getName() + " · "
                + Sales.lkr(c.getAmount()), b.getId());
        return day(b, d);
    }

    /** Close the day (I3, I4): counted vs expected; a difference needs a note. Locks the day and tells the owner. */
    @Transactional
    public ClosingView close(CloseDayForm f) {
        Who w = current.require();
        Branch b = drawerBranch();
        LocalDate d = today();
        lock.checkOpen(b.getId(), d);
        Rates.Today r = rates.today();
        Calc c = calc(b, d);
        Money3 counted = new Money3(f.lkr(), f.usd(), f.eur()).scaled();
        BigDecimal card = f.card().setScale(2, RoundingMode.HALF_UP);
        BigDecimal diff = counted.minus(c.expected).inLkr(r.usd(), r.eur()).add(card.subtract(c.card));
        String note = blank(f.note());
        if (diff.abs().compareTo(TOLERANCE) >= 0 && (note == null || note.length() < 3)) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, NOTE_NEEDED, "Please explain the difference of " + Sales.lkr(diff) + ".",
                    "note", "Add a note for the owner.");
        }
        DayClosing closing = closings.save(new DayClosing(number(b, d), b, d, new DayClosing.Figures(c.expected, counted, c.card, card,
                c.cardFee, c.salesTotal, c.bills, c.drawerOut(), r.usd(), r.eur(), diff), note, Instant.now(), users.getReferenceById(w.id())));
        boolean balanced = diff.abs().compareTo(TOLERANCE) < 0;
        log.add("Day closed", closing.getNumber() + " · difference " + Sales.lkr(diff), b.getId());
        String text = "Day closing " + DMY.format(d) + " · " + b.getDisplayName() + (balanced ? " · balanced" : " · DIFFERENCE " + Sales.lkr(diff));
        alerts.raise(balanced ? AlertType.CLOSING : AlertType.CASH_DIFFERENCE, text, CLOSE_LINK, b.getId());
        if (sendToOwners(closing, text)) {
            closing.sent();
        }
        return view(closing);
    }

    @Transactional(readOnly = true)
    public ClosingView closing(String number) {
        DayClosing c = requireClosing(number);
        return view(c);
    }

    @Transactional(readOnly = true)
    public byte[] closingPdf(String number) {
        return report.pdf(view(requireClosing(number)));
    }

    // ------------------------------------------------------------------ expected cash

    /** What should be in a branch's drawer at the end of a day. Used by the screens, the closing and the sample data. */
    @Transactional(readOnly = true)
    public Money3 expected(long branchId, LocalDate day) {
        return calc(branches.getReferenceById(branchId), day).expected;
    }

    @Transactional(readOnly = true)
    public BigDecimal cardExpected(long branchId, LocalDate day) {
        return calc(branches.getReferenceById(branchId), day).card;
    }

    private Calc calc(Branch b, LocalDate d) {
        Instant from = d.atStartOfDay(zone).toInstant();
        Instant to = d.plusDays(1).atStartOfDay(zone).toInstant();
        Money3 opening = openings.findByBranchIdAndDay(b.getId(), d).map(CashOpening::money).orElse(Money3.ZERO);
        Calc c = new Calc(opening);
        for (Sale s : sales.search(b.getId(), from, to, Sale.Status.COMPLETED)) {
            c.salesList.add(s);
            c.bills++;
            c.salesTotal = c.salesTotal.add(s.getTotal());
            c.cardFee = c.cardFee.add(s.getCardFee());
            for (SalePayment p : s.getPayments()) {
                switch (p.getMethod()) {
                    case CASH -> {
                        c.cashIn = c.cashIn.plus(p.getCurrency(), p.getAmount());
                        c.cashSalesLkr = c.cashSalesLkr.add(p.getLkr());
                    }
                    case CARD -> c.card = c.card.add(p.getLkr());
                    case CREDIT -> { }
                }
            }
            c.change = c.change.add(s.getChangeDue());
        }
        c.cashIn = c.cashIn.plus(lk.nuvi.gemshop.common.money.Currency.LKR, c.change.negate());
        c.cashSalesLkr = c.cashSalesLkr.subtract(c.change);
        c.expenseList = expenses.fromDrawer(b.getId(), from, to);
        c.expensesOut = c.expenseList.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        c.commissionList = commissionPayments.fromDrawer(b.getId(), d);
        c.commissionsOut = c.commissionList.stream().map(CommissionPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        c.refundList = creditNotes.refundedFromDrawer(b.getId(), from, to);
        c.refundsOut = c.refundList.stream().map(CreditNote::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        c.expected = opening.plus(c.cashIn).plus(lk.nuvi.gemshop.common.money.Currency.LKR, c.drawerOut().negate()).scaled();
        return c;
    }

    private static final class Calc {
        final Money3 opening;
        Money3 cashIn = Money3.ZERO;
        Money3 expected;
        BigDecimal card = BigDecimal.ZERO;
        BigDecimal cardFee = BigDecimal.ZERO;
        BigDecimal cashSalesLkr = BigDecimal.ZERO;
        BigDecimal change = BigDecimal.ZERO;
        BigDecimal salesTotal = BigDecimal.ZERO;
        BigDecimal expensesOut = BigDecimal.ZERO;
        BigDecimal commissionsOut = BigDecimal.ZERO;
        BigDecimal refundsOut = BigDecimal.ZERO;
        int bills;
        List<Sale> salesList = new ArrayList<>();
        List<Expense> expenseList = List.of();
        List<CommissionPayment> commissionList = List.of();
        List<CreditNote> refundList = List.of();

        Calc(Money3 opening) {
            this.opening = opening;
        }

        BigDecimal drawerOut() {
            return expensesOut.add(commissionsOut).add(refundsOut);
        }
    }

    // ------------------------------------------------------------------ helpers

    private CashDay day(Branch b, LocalDate d) {
        Calc c = calc(b, d);
        Rates.Today r = rates.today();
        CashOpening o = openings.findByBranchIdAndDay(b.getId(), d).orElse(null);
        DrawerAmounts last = closings.findFirstByBranchIdAndDayBeforeOrderByDayDesc(b.getId(), d).map(x -> amounts(x.counted())).orElse(null);
        ExpectedCash e = new ExpectedCash(amounts(c.opening), amounts(c.cashIn.scaled()), s(c.drawerOut()), amounts(c.expected), s(c.card),
                s(c.cardFee), s(c.cashSalesLkr), s(c.change), s(c.expensesOut), s(c.commissionsOut), s(c.refundsOut), c.bills, s(c.salesTotal));
        Map<String, String> bills = bills(c.expenseList);
        return new CashDay(b.getCode(), b.getDisplayName(), d,
                o == null ? null : new OpeningView(amounts(o.money()), o.getEnteredAt(), o.getEnteredBy().getName()), last,
                openings.findTop10ByBranchIdOrderByDayDesc(b.getId()).stream().map(x -> new OpeningRow(x.getDay(), amounts(x.money()))).toList(),
                e, c.salesList.stream().map(this::payment).toList(),
                c.expenseList.stream().map(x -> row(x, bills)).toList(),
                c.commissionList.stream().map(p -> new DrawerCommissionRow(p.getNumber(), p.getPaidAt(), p.getPayee(), p.getAmount())).toList(),
                c.refundList.stream().map(x -> refund(x, x.getCashRefundAt())).toList(),
                creditNotes.refundsToPay(b.getId()).stream().filter(x -> !x.isUsed()).map(x -> refund(x, x.getIssuedAt())).toList(),
                closings.findByBranchIdAndDay(b.getId(), d).map(this::view).orElse(null), r.usd(), r.eur(),
                lists.values(ListName.EXPENSE_TYPE),
                shops.current().map(ShopSettings::getLargeExpenseLimit).orElse(new BigDecimal("50000")));
    }

    private DayPayment payment(Sale s) {
        String paid = s.getPayments().stream().map(p -> switch (p.getMethod()) {
            case CARD -> "Card";
            case CREDIT -> "Credit note";
            case CASH -> p.getCurrency() + " " + num(p.getAmount());
        }).collect(Collectors.joining(" + "));
        return new DayPayment(s.getNumber(), s.getSoldAt(), paid.isEmpty() ? "—" : paid, s.getChangeDue());
    }

    private Map<String, String> bills(List<Expense> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return files.ofOwners(Expense.OWNER_TYPE, list.stream().map(Expense::getNumber).toList()).stream()
                .collect(Collectors.toMap(StoredFile::getOwnerRef, f -> files.link(f, true), (a, b) -> a));
    }

    private DrawerExpenseRow row(Expense e, Map<String, String> bills) {
        return new DrawerExpenseRow(e.getNumber(), e.getSpentAt(), e.getExpenseType(), e.getPaidTo(), e.getAmount(), e.getStatus(),
                e.getApprovalNo(), bills.get(e.getNumber()));
    }

    private static DrawerRefundRow refund(CreditNote c, Instant at) {
        return new DrawerRefundRow(c.getNumber(), at, c.getSaleReturn().getSale().getCustomer().getName(), c.getSaleReturn().getSale().getNumber(),
                c.getAmount());
    }

    ClosingView view(DayClosing c) {
        return new ClosingView(c.getNumber(), c.getDay(), c.getBranch().getCode(), c.getBranch().getDisplayName(), amounts(c.expected()),
                amounts(c.counted()), c.getCardExpected(), c.getCardCounted(), c.getCardFee(), c.getSalesTotal(), c.getBills(), c.getDrawerOut(),
                c.getRateUsd(), c.getRateEur(), c.getDifference(), c.getNote(), c.getClosedAt(), c.getClosedBy().getName(), c.isSentToOwner());
    }

    private DayClosing requireClosing(String number) {
        DayClosing c = closings.findByNumber(number == null ? "" : number.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("No day closing " + number + "."));
        current.checkBranch(c.getBranch().getId());
        return c;
    }

    /** The closing report goes to every owner by email (I4); the alert shows it in the app. */
    private boolean sendToOwners(DayClosing c, String subject) {
        byte[] pdf = report.pdf(view(c));
        boolean any = false;
        for (UserAccount u : users.findAll()) {
            if (u.getRole() == Role.OWNER && u.isActive() && u.getEmail() != null) {
                String body = subject + "\n\nClosed by " + c.getClosedBy().getName() + " at "
                        + DateTimeFormatter.ofPattern("HH:mm").format(c.getClosedAt().atZone(zone)) + "."
                        + (c.getNote() == null ? "" : "\nNote: " + c.getNote()) + "\n\nThe closing report is attached.";
                any |= mail.send(u.getEmail(), subject, body, "Day closing " + c.getNumber() + ".pdf", pdf, "application/pdf");
            }
        }
        return any;
    }

    /** DC-20261123 for the first branch, DC-20261123-B2 for the others. */
    private String number(Branch b, LocalDate d) {
        boolean main = branches.findAll().stream().map(Branch::getId).min(Long::compare).map(id -> id.equals(b.getId())).orElse(true);
        return "DC-" + YMD.format(d) + (main ? "" : "-" + b.getCode().toUpperCase(Locale.ROOT));
    }

    /** The drawer the screens work on: the branch chosen at the top, the person's own branch, or the only trading branch. */
    Branch drawerBranch() {
        Branch b = drawerBranchOrNull();
        if (b == null) {
            throw ApiException.badRequest(NEED_BRANCH, "Choose the branch at the top first – each branch has its own cash drawer.");
        }
        return b;
    }

    private Branch drawerBranchOrNull() {
        Long id = current.scope().branchId();
        if (id != null) {
            return branches.findById(id).orElse(null);
        }
        List<Branch> trading = branches.findAll().stream().filter(x -> x.getStatus() == BranchStatus.ACTIVE).toList();
        return trading.size() == 1 ? trading.get(0) : null;
    }

    LocalDate today() {
        return LocalDate.now(zone);
    }

    private static DrawerAmounts amounts(Money3 m) {
        return new DrawerAmounts(m.lkr(), m.usd(), m.eur());
    }

    private static BigDecimal s(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String num(BigDecimal v) {
        return new java.text.DecimalFormat("#,##0.##", java.text.DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(v);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
