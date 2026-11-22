package lk.nuvi.gemshop.audit.approval;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lk.nuvi.gemshop.audit.alert.Alerts;
import lk.nuvi.gemshop.audit.api.AuditDtos.ApprovalPage;
import lk.nuvi.gemshop.audit.api.AuditDtos.ApprovalStatusView;
import lk.nuvi.gemshop.audit.api.AuditDtos.ApprovalView;
import lk.nuvi.gemshop.audit.api.AuditDtos.Person;
import lk.nuvi.gemshop.audit.api.AuditDtos.Waiting;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.branch.BranchScope;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.settings.branch.Branch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner approvals (K3). A module asks with {@link #request}; the owner approves or rejects with a note
 * ({@link #decide}); the module's {@link ApprovalHandler} then finishes the action.
 * <p>Every request also raises an owner alert and an activity log line; every decision is logged.
 */
@Service
public class Approvals {

    public static final String LINK = "/approvals";
    public static final int BELL_SIZE = 6;

    private final ApprovalRepository approvals;
    private final Map<ApprovalType, ApprovalHandler> handlers = new EnumMap<>(ApprovalType.class);
    private final Alerts alerts;
    private final ActivityLog log;
    private final CurrentUser currentUser;
    private final EntityManager em;

    public Approvals(ApprovalRepository approvals, List<ApprovalHandler> handlers, Alerts alerts, ActivityLog log,
            CurrentUser currentUser, EntityManager em) {
        this.approvals = approvals;
        for (ApprovalHandler h : handlers) {
            if (this.handlers.put(h.type(), h) != null) {
                throw new IllegalStateException("Two approval handlers for " + h.type());
            }
        }
        this.alerts = alerts;
        this.log = log;
        this.currentUser = currentUser;
        this.em = em;
    }

    /**
     * What a module sends for the owner.
     *
     * @param branchId the branch of the record, or null for the whole shop
     * @param title    short line, e.g. "Below minimum: Cornflower Sapphire Ring (GEM-00113)"
     * @param detail   the reason and figures
     * @param amount   in LKR, or null
     * @param ref      the waiting record (held sale, return, invoice, correction, expense, month)
     */
    public record Request(ApprovalType type, Long branchId, String title, String detail, BigDecimal amount, String ref) {}

    /** Sends a request to the owner (by the signed-in person). Returns it with its number, e.g. AP-0015. */
    @Transactional
    public Approval request(Request r) {
        var who = currentUser.require();
        Branch branch = r.branchId() == null ? null : em.getReference(Branch.class, r.branchId());
        if (r.branchId() != null) {
            currentUser.checkBranch(r.branchId());
        }
        Approval a = approvals.save(new Approval(String.format("AP-%04d", approvals.nextNumber()), r.type(), Instant.now(),
                em.getReference(UserAccount.class, who.id()), branch, r.title(), r.detail(), r.amount(), r.ref()));
        alerts.raise(r.type().alert(), r.title(), LINK + "#" + a.getNumber(), r.branchId());
        log.add("Approval requested", r.title(), r.branchId());
        return a;
    }

    /**
     * The owner approves or rejects. The waiting action then runs ({@link ApprovalHandler}); a request can be
     * decided only once (answer 409 ALREADY_DECIDED, e.g. when the owner taps on two devices).
     */
    @Transactional
    public ApprovalView decide(String number, boolean approve, String note) {
        var who = currentUser.require();
        Approval a = approvals.findByNumber(number).orElseThrow(() -> ApiException.notFound("Approval " + number + " not found."));
        if (!a.isPending()) {
            throw ApiException.conflict("ALREADY_DECIDED", a.getNumber() + " was already " + a.getStatus().name().toLowerCase() + ".");
        }
        a.decide(approve, em.getReference(UserAccount.class, who.id()), note, Instant.now());
        ApprovalHandler h = handlers.get(a.getType());
        if (h != null) {
            h.decided(a, approve);
        }
        String n = a.getNote();
        log.add(approve ? "Approved" : "Rejected", a.getTitle() + (n.isEmpty() ? "" : " — " + n),
                a.getBranch() == null ? null : a.getBranch().getId());
        approvals.flush();
        return view(a, who.name());
    }

    /**
     * Approvals screen: "Pending" or "History" (approved + rejected), optionally one type.
     * Owner and accountant: every request in the top-bar branch; salesperson and staff: their branch and their own.
     */
    @Transactional(readOnly = true)
    public ApprovalPage list(BranchScope scope, boolean pending, ApprovalType type, int page, int size) {
        long me = currentUser.require().id();
        Set<ApprovalStatus> statuses = pending ? EnumSet.of(ApprovalStatus.PENDING)
                : EnumSet.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED);
        Set<ApprovalType> types = type == null ? EnumSet.allOf(ApprovalType.class) : EnumSet.of(type);
        Page<Approval> rows = approvals.search(scope.branchId(), me, statuses, types, PageRequest.of(page, size));
        return new ApprovalPage(rows.map(a -> view(a, null)).getContent(), rows.getTotalElements(), rows.getNumber(),
                rows.getSize(), approvals.countPending(scope.branchId(), me));
    }

    /** The bell and the count on "Approvals" in the menu. */
    @Transactional(readOnly = true)
    public Waiting waiting(BranchScope scope) {
        long me = currentUser.require().id();
        List<ApprovalView> latest = approvals.search(scope.branchId(), me, EnumSet.of(ApprovalStatus.PENDING),
                EnumSet.allOf(ApprovalType.class), PageRequest.of(0, BELL_SIZE)).map(a -> view(a, null)).getContent();
        return new Waiting(approvals.countPending(scope.branchId(), me), latest);
    }

    /** For the "waiting for the owner" screen (asked every few seconds by the person who sent the request). */
    @Transactional(readOnly = true)
    public ApprovalStatusView status(String number, BranchScope scope) {
        Approval a = visible(number, scope);
        return new ApprovalStatusView(a.getNumber(), a.getStatus(), a.getDecidedAt(),
                a.getDecidedBy() == null ? null : a.getDecidedBy().getName(), a.getNote());
    }

    @Transactional(readOnly = true)
    public ApprovalView get(String number, BranchScope scope) {
        return view(visible(number, scope), null);
    }

    private Approval visible(String number, BranchScope scope) {
        long me = currentUser.require().id();
        return approvals.findByNumber(number)
                .filter(a -> a.getBranch() == null || scope.allows(a.getBranch().getId()) || a.getRequestedBy().getId() == me)
                .orElseThrow(() -> ApiException.notFound("Approval " + number + " not found."));
    }

    private static ApprovalView view(Approval a, String decidedByName) {
        UserAccount r = a.getRequestedBy();
        String decidedBy = decidedByName != null ? decidedByName : a.getDecidedBy() == null ? null : a.getDecidedBy().getName();
        return new ApprovalView(a.getNumber(), a.getType(), a.getType().label(), a.getStatus(), a.getRequestedAt(),
                new Person(r.getId(), r.getName(), r.getRole()), a.getBranch() == null ? CurrentUser.ALL : a.getBranch().getCode(),
                a.getTitle(), a.getDetail(), a.getAmount(), a.getRef(), a.getDecidedAt(), decidedBy, a.getNote());
    }
}
