package lk.nuvi.gemshop;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.nuvi.gemshop.common.money.Money;
import lk.nuvi.gemshop.common.persistence.BranchOwnedEntity;
import lk.nuvi.gemshop.settings.branch.Branch;

/** Test only (table from src/test/resources/db/migration): a branch-owned record with Money. */
@Entity
@Table(name = "test_money_record")
public class TestMoneyRecord extends BranchOwnedEntity {

    @Embedded
    private Money money;

    protected TestMoneyRecord() {}

    public TestMoneyRecord(Branch branch, Money money) {
        super(branch);
        this.money = money;
    }

    public Money getMoney() {
        return money;
    }
}
