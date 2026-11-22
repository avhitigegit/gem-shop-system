package lk.nuvi.gemshop.audit.alert;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lk.nuvi.gemshop.audit.api.AuditDtos.AlertView;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.branch.BranchScope;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.paging.PageResult;
import lk.nuvi.gemshop.settings.branch.Branch;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner alerts (K2). Modules raise them: {@code alerts.raise(AlertType.SALE, "Sale INV-000138 · LKR 352,000 · France",
 * "/sell/invoices/INV-000138", branchId)}. Saved with the action (same transaction). Web push to the phone: P4-02.
 */
@Service
public class Alerts {

    private final AlertRepository alerts;
    private final CurrentUser currentUser;
    private final EntityManager em;

    public Alerts(AlertRepository alerts, CurrentUser currentUser, EntityManager em) {
        this.alerts = alerts;
        this.currentUser = currentUser;
        this.em = em;
    }

    /** {@code branchId} null = whole shop. {@code link} = app page to open, or null. */
    @Transactional
    public Alert raise(AlertType type, String text, String link, Long branchId) {
        return raiseAt(Instant.now(), type, text, link, branchId == null ? null : em.getReference(Branch.class, branchId));
    }

    /** Sample data only: an alert with its own time. */
    @Transactional
    public Alert raiseAt(Instant at, AlertType type, String text, String link, Branch branch) {
        return alerts.save(new Alert(at, type, text, link, branch));
    }

    /**
     * @param group      a filter group ({@link AlertType#GROUPS}), or null for all
     * @param unreadOnly only alerts not read yet
     */
    @Transactional(readOnly = true)
    public PageResult<AlertView> list(BranchScope scope, String group, boolean unreadOnly, int page, int size) {
        List<AlertType> types = Arrays.stream(AlertType.values())
                .filter(t -> group == null || group.isBlank() || t.group().equals(group)).toList();
        if (types.isEmpty()) {
            throw ApiException.badRequest("UNKNOWN_GROUP", "Unknown alert group: " + group);
        }
        return PageResult.of(alerts.search(scope.branchId(), types, unreadOnly, PageRequest.of(page, size)), Alerts::view);
    }

    @Transactional(readOnly = true)
    public long unread(BranchScope scope) {
        return alerts.countUnread(scope.branchId());
    }

    @Transactional
    public AlertView markRead(long id, BranchScope scope) {
        Alert a = alerts.findById(id).filter(x -> x.getBranch() == null || scope.allows(x.getBranch().getId()))
                .orElseThrow(() -> ApiException.notFound("Alert not found."));
        a.markRead(me(), Instant.now());
        return view(a);
    }

    /** "Mark all read" – the alerts of the branch in the top bar (all branches: every alert). */
    @Transactional
    public int markAllRead(BranchScope scope) {
        var w = currentUser.require();
        return alerts.markAllRead(scope.branchId(), em.getReference(UserAccount.class, w.id()), w.email(), Instant.now());
    }

    private UserAccount me() {
        return em.getReference(UserAccount.class, currentUser.require().id());
    }

    static AlertView view(Alert a) {
        return new AlertView(a.getId(), a.getAt(), a.getType(), a.getType().group(), a.getText(), a.getLink(),
                a.getBranch() == null ? CurrentUser.ALL : a.getBranch().getCode(), a.isRead());
    }
}
