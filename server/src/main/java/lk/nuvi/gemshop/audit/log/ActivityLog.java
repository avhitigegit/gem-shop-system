package lk.nuvi.gemshop.audit.log;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import lk.nuvi.gemshop.audit.api.AuditDtos.LogLine;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.branch.BranchScope;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.paging.PageResult;
import lk.nuvi.gemshop.settings.branch.Branch;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The activity log (K4). Every module writes here when something happens:
 * {@code activityLog.add("Sale completed", "INV-000138 · Louis Martin · LKR 352,000", branchId)}.
 * <p>The entry is saved in the same transaction as the action, so a failed action leaves no log line.
 * Automatic jobs (no one signed in) are written as "System (automatic)".
 */
@Service
public class ActivityLog {

    private final ActivityLogRepository entries;
    private final EntityManager em;
    private final ZoneId zone;

    public ActivityLog(ActivityLogRepository entries, EntityManager em, AppProperties app) {
        this.entries = entries;
        this.em = em;
        this.zone = ZoneId.of(app.timezone());
    }

    /** By the signed-in person (or the system when no one is signed in). {@code branchId} null = whole shop. */
    @Transactional
    public void add(String action, String detail, Long branchId) {
        var who = CurrentUser.get();
        UserAccount user = who.map(w -> em.getReference(UserAccount.class, w.id())).orElse(null);
        save(Instant.now(), user, who.map(CurrentUser.Who::name).orElse(null), action, detail, branchId);
    }

    /** By a named person, e.g. a sign-in event before the login token exists. */
    @Transactional
    public void addFor(UserAccount user, String action, String detail, Long branchId) {
        save(Instant.now(), user, user == null ? null : user.getName(), action, detail, branchId);
    }

    /** By an automatic job ("System (automatic)"). */
    @Transactional
    public void addSystem(String action, String detail, Long branchId) {
        save(Instant.now(), null, null, action, detail, branchId);
    }

    /** Sample data only: a line with its own time. */
    @Transactional
    public ActivityLogEntry addAt(Instant at, UserAccount user, String action, String detail, Branch branch) {
        return entries.save(new ActivityLogEntry(at, user, user == null ? null : user.getName(), action, detail, branch));
    }

    private void save(Instant at, UserAccount user, String name, String action, String detail, Long branchId) {
        Branch branch = branchId == null ? null : em.getReference(Branch.class, branchId);
        entries.save(new ActivityLogEntry(at, user, name, action, detail, branch));
    }

    /** Search filters. Dates are shop days (Sri Lanka time), both included. */
    public record Filter(LocalDate from, LocalDate to, Long userId, boolean systemOnly, String q) {}

    @Transactional(readOnly = true)
    public PageResult<LogLine> search(BranchScope scope, Filter f, int page, int size) {
        LocalDate to = f.to() == null ? LocalDate.now(zone) : f.to();
        LocalDate from = f.from() == null ? to.minusDays(30) : f.from();
        String q = f.q() == null || f.q().isBlank() ? "%" : "%" + escape(f.q().trim().toLowerCase(Locale.ROOT)) + "%";
        return PageResult.of(entries.search(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant(),
                scope.branchId(), f.systemOnly() ? null : f.userId(), f.systemOnly(), q, PageRequest.of(page, size)),
                ActivityLog::line);
    }

    static LogLine line(ActivityLogEntry e) {
        return new LogLine(e.getAt(), e.getUser() == null ? null : e.getUser().getId(), e.getUserName(), e.getUser() == null,
                e.getAction(), e.getDetail(), e.getBranch() == null ? CurrentUser.ALL : e.getBranch().getCode());
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
