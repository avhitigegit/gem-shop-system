package lk.nuvi.gemshop.dailycash;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lk.nuvi.gemshop.common.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A closed day is locked for its branch (BRD I3): no more opening cash changes and no more cash paid out of that drawer
 * on that day (expenses, refunds, guide commission "Cash (drawer)"). Only repository dependencies, so any module can use
 * it.
 */
@Service
public class DayLock {

    public static final String DAY_CLOSED = "DAY_CLOSED";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DayClosingRepository closings;

    public DayLock(DayClosingRepository closings) {
        this.closings = closings;
    }

    @Transactional(readOnly = true)
    public boolean isClosed(long branchId, LocalDate day) {
        return closings.existsByBranchIdAndDay(branchId, day);
    }

    /** Stops the call when the branch's day is already closed. */
    @Transactional(readOnly = true)
    public void checkOpen(long branchId, LocalDate day) {
        if (isClosed(branchId, day)) {
            throw ApiException.conflict(DAY_CLOSED, "The day " + DMY.format(day) + " is already closed – the cash drawer is locked.");
        }
    }
}
