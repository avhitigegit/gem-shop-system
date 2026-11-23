package lk.nuvi.gemshop.reports;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import lk.nuvi.gemshop.accountant.MonthClosing;
import lk.nuvi.gemshop.accountant.MonthClosingRepository;
import lk.nuvi.gemshop.commissions.CommissionLine;
import lk.nuvi.gemshop.commissions.CommissionLineRepository;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.expenses.Expense;
import lk.nuvi.gemshop.expenses.ExpenseRepository;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.PlExpenseType;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.PlMonth;
import lk.nuvi.gemshop.sales.CreditNote;
import lk.nuvi.gemshop.sales.CreditNoteRepository;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.SaleItem;
import lk.nuvi.gemshop.sales.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Month figures for Profit &amp; Loss, month closing, the accountant home and the tax pack (BRD N4). For a month:
 * <ul>
 *   <li>Sales = invoice value of the month's invoices (cancelled ones left out).</li>
 *   <li>Returns = credit notes given in the month; their gems' cost comes off the cost (the gem is back in stock).</li>
 *   <li>Commissions = guide + staff lines of the month (taken-back lines are negative), cancelled invoices left out.</li>
 *   <li>Card bank fees = the 3% the bank keeps on the month's card payments (a shop cost).</li>
 *   <li>Expenses = approved expenses dated in the month.</li>
 * </ul>
 * Net profit = sales − returns − cost − commissions − card fees − expenses.
 */
@Service
public class Finance {

    private final SaleRepository sales;
    private final CreditNoteRepository creditNotes;
    private final CommissionLineRepository commissions;
    private final ExpenseRepository expenses;
    private final MonthClosingRepository closings;
    private final ZoneId zone;

    public Finance(SaleRepository sales, CreditNoteRepository creditNotes, CommissionLineRepository commissions,
            ExpenseRepository expenses, MonthClosingRepository closings, AppProperties app) {
        this.sales = sales;
        this.creditNotes = creditNotes;
        this.commissions = commissions;
        this.expenses = expenses;
        this.closings = closings;
        this.zone = ZoneId.of(app.timezone());
    }

    /** Everything needed for some months of one branch (or all), read once. */
    public final class Book {
        private final Map<YearMonth, Acc> acc = new TreeMap<>(Comparator.reverseOrder());
        private final Map<YearMonth, Map<String, BigDecimal>> expenseTypes = new LinkedHashMap<>();
        private final Set<String> closed = new HashSet<>();

        private Acc at(YearMonth m) {
            return acc.computeIfAbsent(m, k -> new Acc());
        }

        public PlMonth month(YearMonth m) {
            Acc a = acc.getOrDefault(m, new Acc());
            BigDecimal net = a.sales.subtract(a.returns);
            BigDecimal gross = net.subtract(a.cost);
            return new PlMonth(m.toString(), a.invoices, a.sales, a.returns, net, a.cost, gross, a.comm, a.cardFees, a.expenses,
                    gross.subtract(a.comm).subtract(a.cardFees).subtract(a.expenses), closed.contains(m.toString()));
        }

        /** Months with any figure, plus the current month, newest first. */
        public List<YearMonth> months() {
            Set<YearMonth> s = new TreeSet<>(Comparator.reverseOrder());
            s.addAll(acc.keySet());
            s.add(YearMonth.now(zone));
            return new ArrayList<>(s);
        }

        public List<PlExpenseType> expenseTypes(YearMonth m) {
            return expenseTypes.getOrDefault(m, Map.of()).entrySet().stream()
                    .map(e -> new PlExpenseType(e.getKey(), e.getValue()))
                    .sorted(Comparator.comparing(PlExpenseType::total).reversed()).toList();
        }
    }

    private static final class Acc {
        int invoices;
        BigDecimal sales = BigDecimal.ZERO;
        BigDecimal returns = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal comm = BigDecimal.ZERO;
        BigDecimal cardFees = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
    }

    /**
     * Reads the months {@code from}…{@code to} (null = from the very first record) of a branch (null = all).
     */
    @Transactional(readOnly = true)
    public Book book(Long branch, YearMonth from, YearMonth to) {
        Instant start = from == null ? Instant.EPOCH : from.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = to.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        Book b = new Book();
        closings.findAll().stream().filter(MonthClosing::isClosed).forEach(c -> b.closed.add(c.getMonth().toString()));
        Set<Long> cancelled = new HashSet<>();
        for (Sale s : sales.search(branch, start, end, null)) {
            if (s.getStatus() == Sale.Status.CANCELLED) {
                cancelled.add(s.getId());
                continue;
            }
            Acc a = b.at(month(s.getSoldAt()));
            a.invoices++;
            for (SaleItem i : s.getItems()) {
                a.sales = a.sales.add(i.getAgreed());
                a.cost = a.cost.add(i.getGem().getCost());
            }
            a.cardFees = a.cardFees.add(s.getCardFee());
        }
        for (CreditNote c : creditNotes.list(branch)) {
            if (in(c.getIssuedAt(), start, end)) {
                Acc a = b.at(month(c.getIssuedAt()));
                a.returns = a.returns.add(c.getAmount());
                a.cost = a.cost.subtract(c.getSaleReturn().getGem().getCost());
            }
        }
        for (CommissionLine l : commissions.list(branch)) {
            if (in(l.getEarnedAt(), start, end) && l.getSale().getStatus() != Sale.Status.CANCELLED && !cancelled.contains(l.getSale().getId())) {
                Acc a = b.at(month(l.getEarnedAt()));
                a.comm = a.comm.add(l.getAmount());
            }
        }
        for (Expense e : expenses.all(branch)) {
            if (e.getStatus() == Expense.Status.APPROVED && in(e.getSpentAt(), start, end)) {
                YearMonth m = month(e.getSpentAt());
                Acc a = b.at(m);
                a.expenses = a.expenses.add(e.getAmount());
                b.expenseTypes.computeIfAbsent(m, k -> new TreeMap<>()).merge(e.getExpenseType(), e.getAmount(), BigDecimal::add);
            }
        }
        return b;
    }

    /** "2026-10" → the month; 400 when it is not a month. */
    public static YearMonth parse(String month, String field) {
        try {
            return YearMonth.parse(month.trim());
        } catch (RuntimeException e) {
            throw ApiException.badRequest("BAD_MONTH", "The " + field + " month must look like 2026-10.");
        }
    }

    public YearMonth thisMonth() {
        return YearMonth.now(zone);
    }

    public ZoneId zone() {
        return zone;
    }

    public YearMonth month(Instant at) {
        return YearMonth.from(at.atZone(zone));
    }

    public LocalDate first(YearMonth m) {
        return m.atDay(1);
    }

    private static boolean in(Instant at, Instant start, Instant end) {
        return !at.isBefore(start) && at.isBefore(end);
    }
}
