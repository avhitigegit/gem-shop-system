package lk.nuvi.gemshop.settings.branch;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.settings.api.SettingsDtos.BranchForm;
import lk.nuvi.gemshop.settings.api.SettingsDtos.BranchStaff;
import lk.nuvi.gemshop.settings.api.SettingsDtos.BranchView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Branches (BRD A1, D3, rule 9): Ella now, Branch 2 from January 2027. Opening a branch is a setting change –
 * add it, add its staff, move stock by transfer. Each branch has its own stock, sales and cash.
 * <p>Branches are never deleted (their sales stay): a closed branch is set to Inactive.
 */
@Service
public class Branches {

    public static final String BRANCH_HAS_STAFF = "BRANCH_HAS_STAFF";
    public static final String LAST_BRANCH = "LAST_BRANCH";

    private final BranchRepository branches;
    private final UserAccountRepository users;
    private final ActivityLog log;
    private final ZoneId zone;

    public Branches(BranchRepository branches, UserAccountRepository users, ActivityLog log, AppProperties app) {
        this.branches = branches;
        this.users = users;
        this.log = log;
        this.zone = ZoneId.of(app.timezone());
    }

    @Transactional(readOnly = true)
    public List<BranchView> list() {
        List<UserAccount> all = users.findAll();
        return branches.findAll().stream().sorted(Comparator.comparing(Branch::getId)).map(b -> view(b, all)).toList();
    }

    /** New branch: "Opening" until its opening date, then "Active" ("Mark as open"). */
    @Transactional
    public BranchView add(BranchForm f) {
        LocalDate today = LocalDate.now(zone);
        BranchStatus status = f.openedOn().isAfter(today) ? BranchStatus.OPENING : BranchStatus.ACTIVE;
        Branch b = new Branch(newCode(f.shortName() != null && !f.shortName().isBlank() ? f.shortName() : f.name()),
                f.name().trim(), status);
        b.details(blank(f.shortName()), f.address().trim(), blank(f.phone()), f.openedOn());
        branches.save(b);
        log.add("Branch added", b.getName() + " · " + (status == BranchStatus.OPENING ? "opening " + f.openedOn() : "active"),
                b.getId());
        return view(b, users.findAll());
    }

    @Transactional
    public BranchView edit(String code, BranchForm f) {
        Branch b = get(code);
        b.rename(f.name().trim());
        b.details(blank(f.shortName()), f.address().trim(), blank(f.phone()), f.openedOn());
        log.add("Branch edited", b.getName(), b.getId());
        return view(b, users.findAll());
    }

    /**
     * Active (trading), Opening (not yet) or Inactive (closed). "Mark as open" sets the opening date to today.
     * A branch with active staff cannot be closed (move or switch off their logins first), nor the last open branch.
     */
    @Transactional
    public BranchView setStatus(String code, BranchStatus status) {
        Branch b = get(code);
        if (b.getStatus() == status) {
            return view(b, users.findAll());
        }
        List<UserAccount> all = users.findAll();
        if (status == BranchStatus.INACTIVE) {
            long staff = all.stream().filter(u -> u.isActive() && u.getBranch() != null && u.getBranch().getId().equals(b.getId()))
                    .count();
            if (staff > 0) {
                throw ApiException.conflict(BRANCH_HAS_STAFF, b.getDisplayName() + " still has " + staff
                        + " active login(s). Move them to another branch or switch them off first.");
            }
        }
        if (b.getStatus() == BranchStatus.ACTIVE && branches.findAll().stream()
                .filter(x -> x.getStatus() == BranchStatus.ACTIVE).count() <= 1) {
            throw ApiException.conflict(LAST_BRANCH, "The shop needs at least one active branch.");
        }
        LocalDate today = LocalDate.now(zone);
        if (status == BranchStatus.ACTIVE && (b.getOpenedOn() == null || b.getOpenedOn().isAfter(today))) {
            b.details(b.getShortName(), b.getAddress(), b.getPhone(), today);
        }
        b.setStatus(status);
        log.add(switch (status) {
            case ACTIVE -> "Branch opened";
            case INACTIVE -> "Branch closed";
            case OPENING -> "Branch set to opening";
        }, b.getName(), b.getId());
        return view(b, all);
    }

    private Branch get(String code) {
        return branches.findByCode(code).orElseThrow(() -> ApiException.notFound("Branch not found: " + code));
    }

    /** "Branch 2" → "branch-2"; "Kandy" → "kandy" (or "kandy-2" when taken). The code never changes later. */
    String newCode(String from) {
        String base = Normalizer.normalize(from, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (base.isEmpty() || base.equals("all")) {
            base = "branch";
        }
        if (base.length() > 16) {
            base = base.substring(0, 16).replaceAll("-+$", "");
        }
        String code = base;
        for (int n = 2; branches.findByCode(code).isPresent(); n++) {
            code = base + "-" + n;
        }
        return code;
    }

    private static BranchView view(Branch b, List<UserAccount> all) {
        List<BranchStaff> staff = all.stream()
                .filter(u -> u.getBranch() != null && u.getBranch().getId().equals(b.getId()))
                .sorted(Comparator.comparing(UserAccount::getId))
                .map(u -> new BranchStaff(u.getName(), u.getRole(), u.isActive())).toList();
        return new BranchView(b.getCode(), b.getName(), b.getShortName(), b.getAddress(), b.getPhone(), b.getStatus(),
                b.getOpenedOn(), staff);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
