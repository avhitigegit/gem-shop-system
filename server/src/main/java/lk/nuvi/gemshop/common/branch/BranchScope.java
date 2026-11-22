package lk.nuvi.gemshop.common.branch;

import java.util.Optional;
import lk.nuvi.gemshop.auth.user.Role;

/**
 * "My branch / all branches" (BRD rule 9). Every list of business records is filtered with this.
 * <ul>
 *   <li>Salesperson and Shop Staff: always their own branch, whatever they ask for.</li>
 *   <li>Owner and Accountant: all branches, or one branch they choose.</li>
 * </ul>
 *
 * @param branchId the branch to show, or {@code null} for all branches
 */
public record BranchScope(Long branchId) {

    public static BranchScope all() {
        return new BranchScope(null);
    }

    public static BranchScope of(long branchId) {
        return new BranchScope(branchId);
    }

    public boolean isAll() {
        return branchId == null;
    }

    public Optional<Long> branch() {
        return Optional.ofNullable(branchId);
    }

    /**
     * The scope a user really gets.
     *
     * @param role            the user's role
     * @param ownBranchId     the user's branch ({@code null} for owner / accountant)
     * @param requestedBranch the branch picked in the top bar, or {@code null} for "All branches"
     */
    public static BranchScope resolve(Role role, Long ownBranchId, Long requestedBranch) {
        if (role.seesAllBranches()) {
            return requestedBranch == null ? all() : of(requestedBranch);
        }
        if (ownBranchId == null) {
            throw new IllegalStateException(role + " must belong to a branch");
        }
        return of(ownBranchId);
    }

    /** May this scope see a record of {@code recordBranchId}? */
    public boolean allows(long recordBranchId) {
        return isAll() || branchId == recordBranchId;
    }
}
