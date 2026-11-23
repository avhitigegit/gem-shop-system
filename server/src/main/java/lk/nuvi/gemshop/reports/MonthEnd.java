package lk.nuvi.gemshop.reports;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lk.nuvi.gemshop.accountant.MonthClosing;
import lk.nuvi.gemshop.accountant.MonthClosingRepository;
import lk.nuvi.gemshop.accountant.MonthLock;
import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalRepository;
import lk.nuvi.gemshop.audit.approval.ApprovalStatus;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.audit.approval.Approvals;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.expenses.Expense;
import lk.nuvi.gemshop.expenses.ExpenseRepository;
import lk.nuvi.gemshop.files.FileService;
import lk.nuvi.gemshop.reports.Finance.Book;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.AccountantHome;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.MonthList;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.MonthRow;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.PlBranch;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.PlMonth;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.ProfitLoss;
import lk.nuvi.gemshop.sales.SaleRepository;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profit &amp; Loss, month closing and the accountant home (BRD N1–N6, rule 11). Closing a month locks everything dated in
 * it ({@link MonthLock}); the owner re-opens it with a reason, the accountant asks the owner (MONTH_REOPEN approval).
 */
@Service
public class MonthEnd {

    public static final String MONTH_NOT_ENDED = "MONTH_NOT_ENDED";
    public static final String MONTH_ALREADY_CLOSED = "MONTH_ALREADY_CLOSED";
    public static final String MONTH_NOT_CLOSED = "MONTH_NOT_CLOSED";
    public static final String REOPEN_WAITING = "REOPEN_WAITING";

    private final Finance finance;
    private final MonthClosingRepository closings;
    private final ApprovalRepository approvalRepo;
    private final Approvals approvals;
    private final ExpenseRepository expenses;
    private final SaleRepository sales;
    private final FileService files;
    private final BranchRepository branches;
    private final ShopSettingsRepository shops;
    private final UserAccountRepository users;
    private final ActivityLog log;
    private final CurrentUser current;

    public MonthEnd(Finance finance, MonthClosingRepository closings, ApprovalRepository approvalRepo, Approvals approvals,
            ExpenseRepository expenses, SaleRepository sales, FileService files, BranchRepository branches, ShopSettingsRepository shops,
            UserAccountRepository users, ActivityLog log, CurrentUser current) {
        this.finance = finance;
        this.closings = closings;
        this.approvalRepo = approvalRepo;
        this.approvals = approvals;
        this.expenses = expenses;
        this.sales = sales;
        this.files = files;
        this.branches = branches;
        this.shops = shops;
        this.users = users;
        this.log = log;
        this.current = current;
    }

    // ------------------------------------------------------------------ profit & loss

    /** P&L of a month (empty = last month) for the branch at the top, with every month and the month per branch. */
    @Transactional(readOnly = true)
    public ProfitLoss profitLoss(String month) {
        YearMonth now = finance.thisMonth();
        YearMonth m = month == null || month.isBlank() ? now.minusMonths(1) : Finance.parse(month, "chosen");
        Book book = finance.book(current.scope().branchId(), null, now);
        List<PlBranch> perBranch = new ArrayList<>();
        branches.findAll().forEach(b -> {
            PlMonth x = finance.book(b.getId(), m, m).month(m);
            if (x.invoices() > 0 || x.expenses().signum() != 0 || x.returns().signum() != 0) {
                perBranch.add(new PlBranch(b.getCode(), b.getDisplayName(), x.netSales(), x.netProfit()));
            }
        });
        var pct = shops.current().map(ShopSettings::getCardFeePct).orElse(java.math.BigDecimal.ZERO);
        return new ProfitLoss(book.month(m), book.expenseTypes(m), book.months().stream().map(book::month).toList(), perBranch, pct,
                m.equals(now));
    }

    // ------------------------------------------------------------------ month closing

    @Transactional(readOnly = true)
    public MonthList months() {
        return new MonthList(rows(null));
    }

    /** Rows of every month (newest first, at most {@code limit}), for the whole shop – closing is for the whole shop. */
    private List<MonthRow> rows(Integer limit) {
        YearMonth now = finance.thisMonth();
        Book book = finance.book(null, null, now);
        Map<String, MonthClosing> state = new HashMap<>();
        closings.findAll().forEach(c -> state.put(c.getMonth().toString(), c));
        Map<String, String> reopen = new HashMap<>();
        approvalRepo.findByTypeAndStatus(ApprovalType.MONTH_REOPEN, ApprovalStatus.PENDING)
                .forEach(a -> reopen.put(a.getRef(), a.getNumber()));
        List<Expense> all = expenses.all(null);
        Set<String> withBill = new HashSet<>();
        files.ofOwners(Expense.OWNER_TYPE, all.stream().map(Expense::getNumber).toList()).forEach(f -> withBill.add(f.getOwnerRef()));
        Map<YearMonth, int[]> checks = new HashMap<>();   // [bills missing, expenses waiting]
        for (Expense e : all) {
            int[] c = checks.computeIfAbsent(finance.month(e.getSpentAt()), k -> new int[3]);
            if (e.getStatus() != Expense.Status.REJECTED && e.getSampleBill() == null && !withBill.contains(e.getNumber())) {
                c[0]++;
            }
            if (e.getStatus() == Expense.Status.PENDING) {
                c[1]++;
            }
        }
        for (Approval a : approvalRepo.findByTypeAndStatus(ApprovalType.CANCEL_INVOICE, ApprovalStatus.PENDING)) {
            sales.findByNumber(a.getRef() == null ? "" : a.getRef())
                    .ifPresent(s -> checks.computeIfAbsent(finance.month(s.getSoldAt()), k -> new int[3])[2]++);
        }
        List<YearMonth> months = new ArrayList<>(book.months());
        state.keySet().stream().map(YearMonth::parse).filter(m -> !months.contains(m)).forEach(months::add);
        months.sort(java.util.Comparator.reverseOrder());
        List<MonthRow> out = new ArrayList<>();
        for (YearMonth m : months) {
            if (limit != null && out.size() >= limit) {
                break;
            }
            PlMonth f = book.month(m);
            MonthClosing c = state.get(m.toString());
            int[] k = checks.getOrDefault(m, new int[3]);
            out.add(new MonthRow(m.toString(), f.invoices(), f.netSales(), f.netProfit(), c != null && c.isClosed(),
                    c == null ? null : c.getClosedAt(), c == null ? null : name(c.getClosedBy()),
                    c == null ? null : c.getReopenedAt(), c == null ? null : name(c.getReopenedBy()), c == null ? null : c.getReopenReason(),
                    reopen.get(m.toString()), m.isBefore(now), k[0], k[1], k[2]));
        }
        return out;
    }

    private MonthRow row(YearMonth m) {
        return rows(null).stream().filter(r -> r.month().equals(m.toString())).findFirst()
                .orElseThrow(() -> ApiException.notFound("No month " + m + "."));
    }

    /** Close an ended month (owner or accountant). Nothing dated in it can change afterwards. */
    @Transactional
    public MonthRow close(String month) {
        YearMonth m = Finance.parse(month, "chosen");
        if (!m.isBefore(finance.thisMonth())) {
            throw ApiException.conflict(MONTH_NOT_ENDED, MonthLock.label(m) + " has not ended yet.");
        }
        UserAccount who = users.getReferenceById(current.require().id());
        MonthClosing c = closings.findByMonth(m.toString()).orElse(null);
        if (c != null && c.isClosed()) {
            throw ApiException.conflict(MONTH_ALREADY_CLOSED, MonthLock.label(m) + " is already closed.");
        }
        if (c == null) {
            closings.save(new MonthClosing(m, Instant.now(), who));
        } else {
            c.close(Instant.now(), who);
        }
        log.add("Month closed", MonthLock.label(m), null);
        return row(m);
    }

    /** The owner re-opens a closed month; the accountant asks the owner (approval). The reason is kept in the log. */
    @Transactional
    public MonthRow reopen(String month, String reason) {
        YearMonth m = Finance.parse(month, "chosen");
        MonthClosing c = closings.findByMonth(m.toString()).filter(MonthClosing::isClosed)
                .orElseThrow(() -> ApiException.conflict(MONTH_NOT_CLOSED, MonthLock.label(m) + " is not closed."));
        String why = reason.trim();
        var who = current.require();
        if (who.role() == Role.OWNER) {
            c.reopen(Instant.now(), users.getReferenceById(who.id()), why);
            log.add("Month re-opened", MonthLock.label(m) + " · " + why, null);
        } else {
            boolean waiting = approvalRepo.findByTypeAndStatus(ApprovalType.MONTH_REOPEN, ApprovalStatus.PENDING).stream()
                    .anyMatch(a -> m.toString().equals(a.getRef()));
            if (waiting) {
                throw ApiException.conflict(REOPEN_WAITING, "A request to re-open " + MonthLock.label(m) + " is already waiting for the owner.");
            }
            approvals.request(new Approvals.Request(ApprovalType.MONTH_REOPEN, null, "Re-open closed month " + MonthLock.label(m), why,
                    null, m.toString()));
        }
        return row(m);
    }

    // ------------------------------------------------------------------ accountant home

    @Transactional(readOnly = true)
    public AccountantHome home() {
        YearMonth now = finance.thisMonth();
        Book book = finance.book(current.scope().branchId(), now.minusMonths(1), now);
        List<MonthRow> rows = rows(null);
        int bills = rows.stream().mapToInt(MonthRow::billsMissing).sum();
        int waiting = rows.stream().mapToInt(MonthRow::expensesWaiting).sum();
        YearMonth last = now.minusMonths(1);
        MonthRow toClose = rows.stream().filter(r -> r.month().equals(last.toString())).findFirst()
                .orElse(new MonthRow(last.toString(), 0, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, false, null, null, null, null,
                        null, null, true, 0, 0, 0));
        return new AccountantHome(book.month(now), book.month(last), rows.stream().limit(6).toList(), bills, waiting, toClose);
    }

    private static String name(UserAccount u) {
        return u == null ? null : u.getName();
    }
}
