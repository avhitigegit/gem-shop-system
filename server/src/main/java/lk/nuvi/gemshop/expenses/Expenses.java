package lk.nuvi.gemshop.expenses;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lk.nuvi.gemshop.accountant.MonthLock;
import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.audit.approval.Approvals;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.CurrentUser.Who;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.dailycash.DayLock;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ExpenseDetail;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ExpenseForm;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ExpenseList;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ExpenseMonth;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ExpenseReport;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ExpenseRow;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.MonthlyBillForm;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.MonthlyBillList;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.MonthlyBillRow;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ReportBranchRow;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ReportMethodRow;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ReportMonth;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ReportTypeRow;
import lk.nuvi.gemshop.files.FileKind;
import lk.nuvi.gemshop.files.FileService;
import lk.nuvi.gemshop.files.StoredFile;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.settings.lists.ListName;
import lk.nuvi.gemshop.settings.lists.Lists;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shop expenses (BRD M1–M7; prototype m-expenses/*): bank, card or the owner's own cash, each with an expense type and a bill
 * photo (rule 11); above the owner's limit an expense by the accountant waits for the owner (M6). Cash from the drawer is
 * paid on Pay out cash (Daily cash, P3-06) and counted in Close the day. Monthly bills (rent, phone, salaries) are added by
 * themselves on their day; their bill photo is attached later. The report shows approved expenses by month, type, method
 * and branch. Owner and accountant; the branch at the top filters.
 */
@Service
public class Expenses {

    public static final String USE_PAY_OUT = "USE_PAY_OUT";
    public static final String EXPENSE_LOCKED = "EXPENSE_LOCKED";

    private static final Logger LOG = LoggerFactory.getLogger(Expenses.class);
    private static final String DEFAULT_LIMIT = "50000";

    private final ExpenseRepository expenses;
    private final MonthlyBillRepository bills;
    private final BranchRepository branches;
    private final UserAccountRepository users;
    private final Lists lists;
    private final Numbers numbers;
    private final FileService files;
    private final Approvals approvals;
    private final ShopSettingsRepository shops;
    private final DayLock lock;
    private final MonthLock monthLock;
    private final ActivityLog log;
    private final CurrentUser current;
    private final ZoneId zone;

    public Expenses(ExpenseRepository expenses, MonthlyBillRepository bills, BranchRepository branches, UserAccountRepository users,
            Lists lists, Numbers numbers, FileService files, Approvals approvals, ShopSettingsRepository shops, DayLock lock,
            MonthLock monthLock, ActivityLog log, CurrentUser current, AppProperties app) {
        this.expenses = expenses;
        this.bills = bills;
        this.branches = branches;
        this.users = users;
        this.lists = lists;
        this.numbers = numbers;
        this.files = files;
        this.approvals = approvals;
        this.shops = shops;
        this.lock = lock;
        this.monthLock = monthLock;
        this.log = log;
        this.current = current;
        this.zone = ZoneId.of(app.timezone());
    }

    // ------------------------------------------------------------------ list and one expense

    /**
     * @param month "2026-11" or empty = all months
     * @param status APPROVED, PENDING, REJECTED or empty
     */
    @Transactional(readOnly = true)
    public ExpenseList list(String month, String type, String method, String status) {
        Long scope = current.scope().branchId();
        List<Expense> all = expenses.all(scope);
        Map<String, Integer> months = new TreeMap<>(Comparator.reverseOrder());
        all.forEach(e -> months.merge(month(e.getSpentAt()), 1, Integer::sum));
        Map<String, Boolean> billed = billed(all);
        List<ExpenseRow> rows = all.stream()
                .filter(e -> blank(month) || month.equals(month(e.getSpentAt())))
                .filter(e -> blank(type) || type.equals(e.getExpenseType()))
                .filter(e -> blank(method) || method.equals(e.getMethod().name()))
                .filter(e -> blank(status) || status.equals(e.getStatus().name()))
                .map(e -> row(e, billed.getOrDefault(e.getNumber(), false)))
                .toList();
        BigDecimal total = rows.stream().filter(r -> !"REJECTED".equals(r.status())).map(ExpenseRow::amount).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        return new ExpenseList(rows, total, months.entrySet().stream().map(x -> new ExpenseMonth(x.getKey(), x.getValue())).toList(),
                lists.values(ListName.EXPENSE_TYPE), limit());
    }

    @Transactional(readOnly = true)
    public ExpenseDetail detail(String number) {
        Expense e = require(number);
        List<StoredFile> bill = files.ofOwner(Expense.OWNER_TYPE, number);
        String locked = lockedWhy(e);
        return new ExpenseDetail(row(e, !bill.isEmpty() || e.getSampleBill() != null), bill.isEmpty() ? null : files.view(bill.get(0)),
                e.getSampleBill(), locked == null, locked);
    }

    // ------------------------------------------------------------------ add, correct, bill

    @Transactional
    public ExpenseDetail create(ExpenseForm f) {
        Who w = current.require();
        Expense.Method method = Expense.Method.valueOf(f.method());
        if (method == Expense.Method.DRAWER) {
            throw ApiException.badRequest(USE_PAY_OUT, "Cash paid from the drawer is recorded on Daily cash → Pay out cash from the drawer.");
        }
        if (f.bill() == null) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, "BILL_REQUIRED", "Every expense needs a bill photo (rule 11).", "bill",
                    "Every expense needs a bill photo (rule 11).");
        }
        monthLock.checkOpen(f.spentOn());
        Branch b = branch(f.branch());
        String type = lists.check(ListName.EXPENSE_TYPE, f.expenseType(), "expenseType");
        BigDecimal amount = f.amount().setScale(2, RoundingMode.HALF_UP);
        String no = numbers.next(Counter.EXPENSE);
        boolean big = needsOwner(w, amount);
        Expense e = expenses.save(new Expense(no, at(f.spentOn()), b, type, amount, f.paidTo().trim(), method,
                big ? Expense.Status.PENDING : Expense.Status.APPROVED, blank(f.note()) ? null : f.note().trim(),
                users.getReferenceById(w.id())));
        files.attach(List.of(f.bill()), FileKind.BILL_PHOTO, Expense.OWNER_TYPE, no);
        if (big) {
            ask(e, "Paid to " + e.getPaidTo() + " · " + label(method));
        }
        log.add("Expense recorded", no + " · " + type + " · " + lkr(amount) + (big ? " · waiting for the owner" : ""), b.getId());
        return detail(no);
    }

    /** Correct an expense (M2, N2). A drawer expense stays a drawer expense and only until its day is closed. */
    @Transactional
    public ExpenseDetail correct(String number, ExpenseForm f) {
        Who w = current.require();
        Expense e = require(number);
        String locked = lockedWhy(e);
        if (locked != null) {
            throw ApiException.conflict(EXPENSE_LOCKED, locked);
        }
        monthLock.checkOpen(f.spentOn());
        Expense.Method method = Expense.Method.valueOf(f.method());
        if ((method == Expense.Method.DRAWER) != (e.getMethod() == Expense.Method.DRAWER)) {
            throw ApiException.badRequest(USE_PAY_OUT, "Cash from the drawer cannot be changed to another payment method or back.");
        }
        Branch b = branch(f.branch());
        if (method == Expense.Method.DRAWER && (!b.getId().equals(e.getBranch().getId()) || !f.spentOn().equals(day(e.getSpentAt())))) {
            throw ApiException.badRequest(USE_PAY_OUT, "A drawer expense stays on its day and branch.");
        }
        String type = lists.check(ListName.EXPENSE_TYPE, f.expenseType(), "expenseType");
        BigDecimal amount = f.amount().setScale(2, RoundingMode.HALF_UP);
        List<String> changes = new ArrayList<>();
        diff(changes, "date", day(e.getSpentAt()), f.spentOn());
        diff(changes, "branch", e.getBranch().getDisplayName(), b.getDisplayName());
        diff(changes, "type", e.getExpenseType(), type);
        diff(changes, "amount", lkr(e.getAmount()), lkr(amount));
        diff(changes, "paid to", e.getPaidTo(), f.paidTo().trim());
        diff(changes, "method", label(e.getMethod()), label(method));
        String note = blank(f.note()) ? null : f.note().trim();
        diff(changes, "note", e.getNote(), note);
        boolean raised = amount.compareTo(e.getAmount()) > 0;
        Instant at = f.spentOn().equals(day(e.getSpentAt())) ? e.getSpentAt() : at(f.spentOn());
        e.correct(at, b, type, amount, f.paidTo().trim(), method, note);
        if (f.bill() != null) {
            files.ofOwner(Expense.OWNER_TYPE, number).forEach(files::remove);
            files.attach(List.of(f.bill()), FileKind.BILL_PHOTO, Expense.OWNER_TYPE, number);
            e.sampleBill(null);
            changes.add("new bill photo");
        }
        if (raised && e.getStatus() == Expense.Status.APPROVED && needsOwner(w, amount)) {
            ask(e, "Corrected: paid to " + e.getPaidTo() + " · " + label(method));
            changes.add("waiting for the owner");
        }
        log.add("Expense corrected", number + (changes.isEmpty() ? " · no change" : " · " + String.join(" · ", changes)), b.getId());
        return detail(number);
    }

    /**
     * Attach the bill photo later (e.g. a monthly bill that was added by itself). In a closed month only a missing bill
     * photo may still be added – an existing one is not replaced.
     */
    @Transactional
    public ExpenseDetail attachBill(String number, java.util.UUID bill) {
        Expense e = require(number);
        if (monthLock.isClosed(day(e.getSpentAt())) && (!files.ofOwner(Expense.OWNER_TYPE, number).isEmpty() || e.getSampleBill() != null)) {
            throw ApiException.conflict(EXPENSE_LOCKED, monthLock.closedWhy(day(e.getSpentAt())));
        }
        files.ofOwner(Expense.OWNER_TYPE, number).forEach(files::remove);
        files.attach(List.of(bill), FileKind.BILL_PHOTO, Expense.OWNER_TYPE, number);
        e.sampleBill(null);
        log.add("Bill photo added", number + " · " + e.getExpenseType(), e.getBranch().getId());
        return detail(number);
    }

    // ------------------------------------------------------------------ monthly bills

    @Transactional(readOnly = true)
    public MonthlyBillList monthlyBills() {
        Long scope = current.scope().branchId();
        LocalDate today = LocalDate.now(zone);
        List<MonthlyBillRow> rows = bills.findAllByOrderByDayAscCodeAsc().stream()
                .filter(b -> scope == null || scope.equals(b.getBranch().getId()))
                .map(b -> billRow(b, today)).toList();
        BigDecimal total = rows.stream().filter(MonthlyBillRow::active).map(MonthlyBillRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MonthlyBillList(rows, lists.values(ListName.EXPENSE_TYPE), total);
    }

    @Transactional
    public MonthlyBillRow saveBill(String code, MonthlyBillForm f) {
        Branch b = branch(f.branch());
        String type = lists.check(ListName.EXPENSE_TYPE, f.expenseType(), "expenseType");
        BigDecimal amount = f.amount().setScale(2, RoundingMode.HALF_UP);
        Expense.Method method = Expense.Method.valueOf(f.method());
        MonthlyBill bill;
        if (code == null) {
            bill = bills.save(new MonthlyBill(nextCode(), b, type, amount, f.paidTo().trim(), method, f.day()));
            log.add("Monthly bill added", bill.getCode() + " · " + type + " · " + lkr(amount) + " · day " + f.day(), b.getId());
        } else {
            bill = bills.findByCode(code).orElseThrow(() -> ApiException.notFound("Monthly bill " + code + " not found."));
            bill.change(b, type, amount, f.paidTo().trim(), method, f.day());
            log.add("Monthly bill changed", code + " · " + type + " · " + lkr(amount) + " · day " + f.day(), b.getId());
        }
        if (f.active() != null && f.active() != bill.isActive()) {
            bill.active(f.active());
            log.add(f.active() ? "Monthly bill on" : "Monthly bill paused", bill.getCode() + " · " + type, b.getId());
        }
        return billRow(bill, LocalDate.now(zone));
    }

    /** "Add this month's bills now" (owner / accountant): every active bill not added this month, on its day or earlier. */
    @Transactional
    public int runNow() {
        int n = run(LocalDate.now(zone), true);
        log.add("Monthly bills added", n + " expense(s)", null);
        return n;
    }

    /** Every night: active bills whose day has come and that were not added this month. */
    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Colombo")
    @Transactional
    public void nightly() {
        try {
            int n = run(LocalDate.now(zone), false);
            if (n > 0) {
                LOG.info("{} monthly bill(s) added", n);
            }
        } catch (RuntimeException e) {
            LOG.warn("Monthly bills not added: {}", e.toString());
        }
    }

    /**
     * @param now true = the button: also bills whose day is still to come this month (dated today)
     */
    int run(LocalDate today, boolean now) {
        YearMonth ym = YearMonth.from(today);
        Instant from = ym.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        int n = 0;
        for (MonthlyBill b : bills.findAllByOrderByDayAscCodeAsc()) {
            if (!b.isActive() || (!now && b.getDay() > today.getDayOfMonth()) || expenses.addedFor(b, from, to)) {
                continue;
            }
            LocalDate on = b.getDay() <= today.getDayOfMonth() ? ym.atDay(b.getDay()) : today;
            String no = numbers.next(Counter.EXPENSE);
            Expense e = new Expense(no, on.atTime(LocalTime.NOON).atZone(zone).toInstant(), b.getBranch(), b.getExpenseType(), b.getAmount(),
                    b.getPaidTo(), b.getMethod(), Expense.Status.APPROVED, "Added automatically (monthly bill " + b.getCode() + ")", null);
            e.fromMonthlyBill(b);
            expenses.save(e);
            b.added(on);
            log.addSystem("Monthly bill added", no + " · " + b.getExpenseType() + " · " + lkr(b.getAmount()) + " · bill photo to attach",
                    b.getBranch().getId());
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ report

    /** Approved expenses by month, expense type, payment method and branch. */
    @Transactional(readOnly = true)
    public ExpenseReport report() {
        Long scope = current.scope().branchId();
        List<Expense> ok = expenses.all(scope).stream().filter(e -> e.getStatus() == Expense.Status.APPROVED).toList();
        List<String> months = ok.stream().map(e -> month(e.getSpentAt())).distinct().sorted().toList();
        Map<String, Map<String, BigDecimal>> byType = new TreeMap<>();
        Map<String, BigDecimal> byMonth = new LinkedHashMap<>();
        Map<String, int[]> methodCount = new LinkedHashMap<>();
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        Map<String, BigDecimal> byBranch = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Expense e : ok) {
            String m = month(e.getSpentAt());
            byType.computeIfAbsent(e.getExpenseType(), k -> new LinkedHashMap<>()).merge(m, e.getAmount(), BigDecimal::add);
            byMonth.merge(m, e.getAmount(), BigDecimal::add);
            methodCount.computeIfAbsent(e.getMethod().name(), k -> new int[1])[0]++;
            byMethod.merge(e.getMethod().name(), e.getAmount(), BigDecimal::add);
            byBranch.merge(e.getBranch().getDisplayName(), e.getAmount(), BigDecimal::add);
            total = total.add(e.getAmount());
        }
        List<ReportTypeRow> types = byType.entrySet().stream().map(x -> new ReportTypeRow(x.getKey(),
                months.stream().map(m -> x.getValue().getOrDefault(m, BigDecimal.ZERO)).toList(),
                x.getValue().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(ReportTypeRow::total).reversed()).toList();
        return new ExpenseReport(months.stream().map(m -> new ReportMonth(m, byMonth.get(m))).toList(), types,
                byMethod.entrySet().stream().map(x -> new ReportMethodRow(x.getKey(), methodCount.get(x.getKey())[0], x.getValue()))
                        .sorted(Comparator.comparing(ReportMethodRow::total).reversed()).toList(),
                byBranch.entrySet().stream().map(x -> new ReportBranchRow(x.getKey(), x.getValue())).toList(), total);
    }

    // ------------------------------------------------------------------ helpers

    private Expense require(String number) {
        Expense e = expenses.findByNumber(number).orElseThrow(() -> ApiException.notFound("Expense " + number + " not found."));
        current.checkBranch(e.getBranch().getId());
        return e;
    }

    /** Why the expense cannot be corrected, or null. */
    private String lockedWhy(Expense e) {
        if (e.getStatus() == Expense.Status.REJECTED) {
            return "The owner rejected this expense.";
        }
        String month = monthLock.closedWhy(day(e.getSpentAt()));
        if (month != null) {
            return month;
        }
        if (e.getMethod() == Expense.Method.DRAWER && lock.isClosed(e.getBranch().getId(), day(e.getSpentAt()))) {
            return "Paid from the drawer on a day that is already closed.";
        }
        return null;
    }

    private void ask(Expense e, String detail) {
        BigDecimal limit = limit();
        Approval a = approvals.request(new Approvals.Request(ApprovalType.EXPENSE, e.getBranch().getId(),
                "Large expense: " + e.getExpenseType() + " " + lkr(e.getAmount()), detail + " (limit " + lkr(limit) + ")", e.getAmount(),
                e.getNumber()));
        e.waitForOwner(a.getNumber());
    }

    /** Above the limit and not the owner (the owner's own expenses need no approval). */
    private boolean needsOwner(Who w, BigDecimal amount) {
        return w.role() != Role.OWNER && amount.compareTo(limit()) > 0;
    }

    private BigDecimal limit() {
        return shops.current().map(ShopSettings::getLargeExpenseLimit).orElse(new BigDecimal(DEFAULT_LIMIT));
    }

    private Branch branch(String code) {
        Branch b = branches.findByCode(code).orElseThrow(() -> ApiException.field(HttpStatus.BAD_REQUEST, "UNKNOWN_BRANCH",
                "Choose the branch.", "branch", "Choose the branch."));
        current.checkBranch(b.getId());
        return b;
    }

    /** Today = now; an earlier day = noon that day (shop time). No future days. */
    private Instant at(LocalDate on) {
        LocalDate today = LocalDate.now(zone);
        if (on.isAfter(today)) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please check the highlighted fields.", "spentOn",
                    "The date cannot be in the future.");
        }
        return on.equals(today) ? Instant.now() : on.atTime(LocalTime.NOON).atZone(zone).toInstant();
    }

    private MonthlyBillRow billRow(MonthlyBill b, LocalDate today) {
        YearMonth ym = YearMonth.from(today);
        boolean added = expenses.addedFor(b, ym.atDay(1).atStartOfDay(zone).toInstant(),
                ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant());
        LocalDate next = !b.isActive() ? null : !added && b.getDay() >= today.getDayOfMonth() ? ym.atDay(b.getDay())
                : !added ? today : ym.plusMonths(1).atDay(b.getDay());
        return new MonthlyBillRow(b.getCode(), b.getBranch().getCode(), b.getExpenseType(), b.getAmount(), b.getPaidTo(),
                b.getMethod().name(), b.getDay(), b.isActive(), b.getLastAdded(), next, added);
    }

    private String nextCode() {
        int max = bills.findAll().stream().map(MonthlyBill::getCode).map(c -> c.replaceAll("[^0-9]", ""))
                .filter(s -> !s.isEmpty()).mapToInt(Integer::parseInt).max().orElse(0);
        return String.format("RC-%02d", max + 1);
    }

    private Map<String, Boolean> billed(List<Expense> list) {
        Map<String, Boolean> m = new java.util.HashMap<>();
        list.stream().filter(e -> e.getSampleBill() != null).forEach(e -> m.put(e.getNumber(), true));
        files.ofOwners(Expense.OWNER_TYPE, list.stream().map(Expense::getNumber).collect(Collectors.toSet()))
                .forEach(f -> m.put(f.getOwnerRef(), true));
        return m;
    }

    private ExpenseRow row(Expense e, boolean hasBill) {
        return new ExpenseRow(e.getNumber(), e.getSpentAt(), e.getBranch().getCode(), e.getExpenseType(), e.getPaidTo(),
                e.getMethod().name(), e.getAmount(), e.getStatus().name(), e.getApprovalNo(), e.getNote(),
                e.getMonthlyBill() == null ? null : e.getMonthlyBill().getCode(), hasBill,
                e.getStaff() == null ? "System" : e.getStaff().getName());
    }

    private String month(Instant at) {
        return YearMonth.from(at.atZone(zone)).toString();
    }

    private LocalDate day(Instant at) {
        return at.atZone(zone).toLocalDate();
    }

    static String label(Expense.Method m) {
        return switch (m) {
            case DRAWER -> "Cash (drawer)";
            case OWNER_CASH -> "Cash (owner)";
            case BANK -> "Bank transfer";
            case CARD -> "Card";
        };
    }

    private static void diff(List<String> out, String what, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            out.add(what + " " + (before == null ? "—" : before) + " → " + (after == null ? "—" : after));
        }
    }

    static String lkr(BigDecimal v) {
        return "LKR " + String.format(java.util.Locale.ROOT, "%,.2f", v);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
