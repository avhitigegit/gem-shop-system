package lk.nuvi.gemshop.common.persistence;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import java.util.Objects;
import lk.nuvi.gemshop.settings.branch.Branch;

/**
 * Every business record (gem, sale, expense, cash day, ...) belongs to one branch (BRD rule 9).
 * Column: {@code branch_id bigint not null references branch(id)} plus an index on it.
 * Lists use {@link lk.nuvi.gemshop.common.branch.BranchScope} for "my branch / all branches".
 */
@MappedSuperclass
public abstract class BranchOwnedEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    protected BranchOwnedEntity() {}

    protected BranchOwnedEntity(Branch branch) {
        this.branch = Objects.requireNonNull(branch, "branch");
    }

    public Branch getBranch() {
        return branch;
    }

    protected void moveTo(Branch branch) {
        this.branch = Objects.requireNonNull(branch, "branch");
    }
}
