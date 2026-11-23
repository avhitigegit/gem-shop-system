package lk.nuvi.gemshop.accountant;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import lk.nuvi.gemshop.common.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A closed month is locked (BRD N6, rule 11): sales cancellations, expenses, purchases and commission payments dated in it
 * cannot be added or changed until the owner re-opens the month. Only a repository dependency, so any module can use it.
 * <pre>
 * monthLock.checkOpen(f.spentOn(), "add an expense");   // 409 MONTH_CLOSED
 * </pre>
 */
@Service
public class MonthLock {

    public static final String MONTH_CLOSED = "MONTH_CLOSED";

    private final MonthClosingRepository months;

    public MonthLock(MonthClosingRepository months) {
        this.months = months;
    }

    @Transactional(readOnly = true)
    public boolean isClosed(LocalDate day) {
        return day != null && months.existsByMonthAndStatus(YearMonth.from(day).toString(), MonthClosing.Status.CLOSED);
    }

    /** The closed months, for marking rows of a list (one query). */
    @Transactional(readOnly = true)
    public java.util.Set<YearMonth> closedMonths() {
        java.util.Set<YearMonth> out = new java.util.HashSet<>();
        months.findAll().stream().filter(MonthClosing::isClosed).forEach(c -> out.add(c.getMonth()));
        return out;
    }

    /** "September 2026 is closed …" when the day's month is closed, otherwise null. */
    @Transactional(readOnly = true)
    public String closedWhy(LocalDate day) {
        return isClosed(day) ? label(YearMonth.from(day)) + " is closed. Ask the owner to re-open the month first (rule 11)." : null;
    }

    /** Stops the call when the day's month is closed. */
    @Transactional(readOnly = true)
    public void checkOpen(LocalDate day) {
        String why = closedWhy(day);
        if (why != null) {
            throw ApiException.conflict(MONTH_CLOSED, why);
        }
    }

    /** 2026-09 → "September 2026". */
    public static String label(YearMonth m) {
        return m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear();
    }
}
