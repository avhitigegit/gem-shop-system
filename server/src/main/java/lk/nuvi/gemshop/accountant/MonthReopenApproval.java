package lk.nuvi.gemshop.accountant;

import java.time.Instant;
import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalHandler;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import org.springframework.stereotype.Component;

/** The owner decided "Re-open closed month" (asked by the accountant): approved → the month is open again (rule 11). */
@Component
public class MonthReopenApproval implements ApprovalHandler {

    private final MonthClosingRepository months;
    private final UserAccountRepository users;
    private final ActivityLog log;

    public MonthReopenApproval(MonthClosingRepository months, UserAccountRepository users, ActivityLog log) {
        this.months = months;
        this.users = users;
        this.log = log;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.MONTH_REOPEN;
    }

    @Override
    public void decided(Approval a, boolean approved) {
        if (!approved || a.getRef() == null) {
            return;
        }
        months.findByMonth(a.getRef()).filter(MonthClosing::isClosed).ifPresent(m -> {
            var owner = CurrentUser.get().flatMap(w -> users.findById(w.id())).orElse(null);
            m.reopen(Instant.now(), owner, a.getDetail());
            log.add("Month re-opened", MonthLock.label(m.getMonth()) + " · " + a.getDetail() + " (" + a.getNumber() + ")", null);
        });
    }
}
