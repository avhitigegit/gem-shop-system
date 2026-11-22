package lk.nuvi.gemshop.common.branch;

import lk.nuvi.gemshop.common.persistence.BranchOwnedEntity;
import org.springframework.data.jpa.domain.Specification;

/** Query filter for repositories of branch-owned records ({@code JpaSpecificationExecutor}). */
public final class BranchSpecifications {

    private BranchSpecifications() {}

    public static <T extends BranchOwnedEntity> Specification<T> inScope(BranchScope scope) {
        return (root, query, cb) -> scope.isAll()
                ? cb.conjunction()
                : cb.equal(root.get("branch").get("id"), scope.branchId());
    }
}
