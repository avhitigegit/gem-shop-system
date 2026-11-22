package lk.nuvi.gemshop.settings.staff;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.AuthService;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.settings.api.SettingsDtos.StaffForm;
import lk.nuvi.gemshop.settings.api.SettingsDtos.StaffView;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.settings.branch.BranchStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff logins (BRD A2, §03, N1, N2, rule 1): each person has their own login and role, linked to a branch.
 * A new login gets a welcome email with a link to choose a password (shop SMTP; MailHog until P6-03).
 * Logins are never deleted (their sales and log lines stay): they are switched off.
 */
@Service
public class Staff {

    public static final String EMAIL_TAKEN = "EMAIL_TAKEN";
    public static final String OWN_LOGIN = "OWN_LOGIN";
    public static final String LAST_OWNER = "LAST_OWNER";
    public static final String BRANCH_NEEDED = "BRANCH_NEEDED";
    public static final String LOGIN_OFF = "LOGIN_OFF";

    private final UserAccountRepository users;
    private final BranchRepository branches;
    private final AuthService auth;
    private final CurrentUser current;
    private final ActivityLog log;

    public Staff(UserAccountRepository users, BranchRepository branches, AuthService auth, CurrentUser current, ActivityLog log) {
        this.users = users;
        this.branches = branches;
        this.auth = auth;
        this.current = current;
        this.log = log;
    }

    @Transactional(readOnly = true)
    public List<StaffView> list() {
        long me = current.require().id();
        return users.findAll().stream()
                .sorted(Comparator.comparing((UserAccount u) -> !u.isActive()).thenComparing(u -> u.getRole().ordinal())
                        .thenComparing(UserAccount::getId))
                .map(u -> view(u, me)).toList();
    }

    /** New login; a welcome email with a "choose your password" link is sent when it is active. */
    @Transactional
    public StaffView add(StaffForm f) {
        String email = UserAccount.normalise(f.email());
        users.findByEmail(email).ifPresent(u -> {
            throw emailTaken();
        });
        UserAccount u = new UserAccount(f.name().trim(), email, f.role(), branch(f));
        u.phone(blank(f.phone()));
        u.setActive(f.active());
        users.save(u);
        if (u.isActive()) {
            auth.sendPasswordLink(u, true);
        }
        log.add("User added", u.getName() + " · " + u.getRole().label() + (u.isActive() ? " · invitation emailed" : ""),
                branchId(u));
        return view(u, current.require().id());
    }

    @Transactional
    public StaffView edit(long id, StaffForm f) {
        UserAccount u = get(id);
        long me = current.require().id();
        String email = UserAccount.normalise(f.email());
        users.findByEmail(email).filter(x -> !x.getId().equals(u.getId())).ifPresent(x -> {
            throw emailTaken();
        });
        if (u.getId() == me && (f.role() != u.getRole() || !f.active())) {
            throw ApiException.conflict(OWN_LOGIN, "You cannot change your own role or switch off your own login.");
        }
        boolean wasOwner = u.getRole() == Role.OWNER && u.isActive();
        if (wasOwner && (f.role() != Role.OWNER || !f.active()) && activeOwners() <= 1) {
            throw ApiException.conflict(LAST_OWNER, "The shop needs at least one active owner login.");
        }
        Branch b = branch(f);
        boolean access = f.role() != u.getRole() || f.active() != u.isActive() || !same(b, u.getBranch())
                || !email.equals(u.getEmail());
        u.rename(f.name().trim(), email);
        u.phone(blank(f.phone()));
        u.assign(f.role(), b);
        u.setActive(f.active());
        if (access) {
            // new role / branch / email, or switched off: sign in again (the open page follows within minutes)
            auth.signOutEverywhere(u);
        }
        log.add("User edited", u.getName() + " · " + u.getRole().label() + (u.isActive() ? "" : " · switched off"), branchId(u));
        return view(u, me);
    }

    /**
     * "Reset password": emails a link to choose a new password (a person who never chose one gets the welcome
     * email again) and unlocks the login.
     */
    @Transactional
    public void sendPasswordLink(long id) {
        UserAccount u = get(id);
        if (!u.isActive()) {
            throw ApiException.conflict(LOGIN_OFF, u.getName() + "'s login is switched off. Switch it on first.");
        }
        u.unlock();
        boolean invite = u.getPasswordHash() == null;
        auth.sendPasswordLink(u, invite);
        log.add(invite ? "Invitation sent again" : "Password reset sent", u.getName() + " · " + u.getEmail(), branchId(u));
    }

    /** Unlock a login that was locked after too many wrong passwords. */
    @Transactional
    public StaffView unlock(long id) {
        UserAccount u = get(id);
        u.unlock();
        log.add("Login unlocked", u.getName(), branchId(u));
        return view(u, current.require().id());
    }

    private long activeOwners() {
        return users.findAll().stream().filter(x -> x.isActive() && x.getRole() == Role.OWNER).count();
    }

    private Branch branch(StaffForm f) {
        if (f.role().seesAllBranches()) {
            return null;
        }
        if (f.branch() == null || f.branch().isBlank() || "all".equalsIgnoreCase(f.branch())) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, BRANCH_NEEDED, f.role().label() + " must belong to a branch.",
                    "branch", "Choose a branch.");
        }
        Branch b = branches.findByCode(f.branch()).orElseThrow(() -> ApiException.field(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED, "Unknown branch: " + f.branch(), "branch", "Choose a branch."));
        if (b.getStatus() == BranchStatus.INACTIVE) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, BRANCH_NEEDED, b.getDisplayName() + " is closed.", "branch",
                    "This branch is closed.");
        }
        return b;
    }

    private UserAccount get(long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Login not found."));
    }

    private static ApiException emailTaken() {
        return ApiException.field(HttpStatus.CONFLICT, EMAIL_TAKEN, "Another login already uses this email.", "email",
                "Already used by another login.");
    }

    private static boolean same(Branch a, Branch b) {
        return a == null ? b == null : b != null && a.getId().equals(b.getId());
    }

    private static Long branchId(UserAccount u) {
        return u.getBranch() == null ? null : u.getBranch().getId();
    }

    private static StaffView view(UserAccount u, long me) {
        return new StaffView(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(),
                u.getBranch() == null ? null : u.getBranch().getCode(), u.isActive(), u.isLocked(Instant.now()),
                u.getPasswordHash() != null, u.getLastLoginAt(), u.getId() == me);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
