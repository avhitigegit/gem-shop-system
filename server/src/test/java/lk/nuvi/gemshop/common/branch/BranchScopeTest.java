package lk.nuvi.gemshop.common.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lk.nuvi.gemshop.auth.user.Role;
import org.junit.jupiter.api.Test;

class BranchScopeTest {

    @Test
    void salespersonAndStaffAlwaysGetTheirOwnBranch() {
        assertThat(BranchScope.resolve(Role.SALESPERSON, 1L, null)).isEqualTo(BranchScope.of(1));
        assertThat(BranchScope.resolve(Role.STAFF, 1L, 2L)).isEqualTo(BranchScope.of(1));
    }

    @Test
    void ownerAndAccountantChooseAllOrOneBranch() {
        assertThat(BranchScope.resolve(Role.OWNER, null, null).isAll()).isTrue();
        assertThat(BranchScope.resolve(Role.ACCOUNTANT, null, 2L)).isEqualTo(BranchScope.of(2));
    }

    @Test
    void branchRoleWithoutBranchIsAnError() {
        assertThatThrownBy(() -> BranchScope.resolve(Role.SALESPERSON, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsRecordsOfItsBranchOnly() {
        assertThat(BranchScope.of(1).allows(1)).isTrue();
        assertThat(BranchScope.of(1).allows(2)).isFalse();
        assertThat(BranchScope.all().allows(2)).isTrue();
    }
}
