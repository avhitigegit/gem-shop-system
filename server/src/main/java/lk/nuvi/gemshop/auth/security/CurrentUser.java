package lk.nuvi.gemshop.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.common.branch.BranchScope;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The signed-in person, for services: id, role, branch, and the branch check (BRD rule 9).
 * The branch picked in the top bar comes in the {@code X-Branch} header ("ella", "b2" or "all").
 */
@Component
public class CurrentUser {

    public static final String BRANCH_HEADER = "X-Branch";
    public static final String ALL = "all";

    private final BranchRepository branches;

    public CurrentUser(BranchRepository branches) {
        this.branches = branches;
    }

    public record Who(long id, String email, String name, Role role, Long branchId) {}

    /** The signed-in person, or empty for public calls and automatic jobs. */
    public static Optional<Who> get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (!(a instanceof JwtAuthenticationToken t)) {
            return Optional.empty();
        }
        var jwt = t.getToken();
        Object b = jwt.getClaims().get(TokenService.BRANCH);
        return Optional.of(new Who(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString(TokenService.EMAIL),
                jwt.getClaimAsString(TokenService.NAME), Role.valueOf(jwt.getClaimAsString(TokenService.ROLE)),
                b == null ? null : ((Number) b).longValue()));
    }

    public Who require() {
        return get().orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                lk.nuvi.gemshop.common.error.ErrorCode.UNAUTHORIZED, "Please sign in."));
    }

    public static Optional<Role> role() {
        return get().map(Who::role);
    }

    /** "My branch / all branches" for lists: salesperson and staff always get their own branch. */
    public BranchScope scope() {
        Who w = require();
        String asked = header();
        Long requested = null;
        if (asked != null && !asked.isBlank() && !ALL.equalsIgnoreCase(asked)) {
            requested = branches.findByCode(asked).map(Branch::getId)
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_BRANCH", "Unknown branch: " + asked));
        }
        return BranchScope.resolve(w.role(), w.branchId(), requested);
    }

    /** Stops the call when the person may not work with records of this branch. */
    public void checkBranch(long branchId) {
        Who w = require();
        if (!w.role().seesAllBranches() && (w.branchId() == null || w.branchId() != branchId)) {
            throw ApiException.forbidden("This record belongs to another branch.");
        }
    }

    private static String header() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes s) {
            HttpServletRequest r = s.getRequest();
            return r.getHeader(BRANCH_HEADER);
        }
        return null;
    }
}
