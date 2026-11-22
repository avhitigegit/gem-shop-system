package lk.nuvi.gemshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.branch.BranchScope;
import lk.nuvi.gemshop.common.branch.BranchSpecifications;
import lk.nuvi.gemshop.common.money.Currency;
import lk.nuvi.gemshop.common.money.Money;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.settings.branch.BranchStatus;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import lk.nuvi.gemshop.system.sampledata.SampleDataLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** P1-01: base fields, money, branch filter, the three tables and the sample data (real PostgreSQL). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BaseDataTest {

    @Autowired BranchRepository branches;
    @Autowired UserAccountRepository users;
    @Autowired ShopSettingsRepository shops;
    @Autowired TestMoneyRecordRepository moneyRecords;
    @Autowired SampleDataLoader loader;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    // ---------------------------------------------------------------- sample data (= prototype v12)

    @Test
    void sampleDataHasTheTwoPrototypeBranches() {
        Branch ella = branches.findByCode("ella").orElseThrow();
        Branch b2 = branches.findByCode("b2").orElseThrow();
        assertThat(ella.getName()).isEqualTo("Ella");
        assertThat(ella.getStatus()).isEqualTo(BranchStatus.ACTIVE);
        assertThat(b2.getDisplayName()).isEqualTo("Branch 2");
        assertThat(b2.getStatus()).isEqualTo(BranchStatus.OPENING);
        assertThat(branches.count()).isEqualTo(2);
    }

    @Test
    void sampleDataHasOneUserPerRole() {
        tx.executeWithoutResult(s -> {
            assertThat(users.findAll()).extracting(UserAccount::getRole)
                    .containsExactlyInAnyOrder(Role.OWNER, Role.SALESPERSON, Role.STAFF, Role.ACCOUNTANT);
            UserAccount owner = users.findByEmail("owner@ceylongemhouse.lk").orElseThrow();
            assertThat(owner.getName()).isEqualTo("Ruwan Perera");
            assertThat(owner.getBranch()).isNull(); // all branches
            UserAccount sales = users.findByEmail("nimal@ceylongemhouse.lk").orElseThrow();
            assertThat(sales.getRole()).isEqualTo(Role.SALESPERSON);
            assertThat(sales.getBranch().getCode()).isEqualTo("ella");
            assertThat(users.findByEmail("dilani@fernandoassociates.lk").orElseThrow().getBranch()).isNull();
        });
    }

    @Test
    void sampleShopDetailsMatchThePrototype() {
        ShopSettings shop = shops.current().orElseThrow();
        assertThat(shop.getName()).isEqualTo("Ceylon Gem House");
        assertThat(shop.getNgjaLicenceNo()).isEqualTo("NGJA/DL/2024/0457");
        assertThat(shop.getCardFeePct()).isEqualByComparingTo("3");
        assertThat(shop.getReturnPolicyDays()).isEqualTo(14);
        // the prototype sets the licence to expire 45 days from today, so the reminder shows
        assertThat(shop.getNgjaLicenceExpiry()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Colombo")).plusDays(45));
    }

    @Test
    void noVatColumnsAnywhere() {
        Integer n = jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'public' and column_name like '%vat%'",
                Integer.class);
        assertThat(n).isZero();
    }

    @Test
    void sampleDataIsNotLoadedTwice() {
        loader.loadOnStartup(); // database already has data
        assertThat(branches.count()).isEqualTo(2);
        assertThat(users.count()).isEqualTo(4);
    }

    // ---------------------------------------------------------------- base fields

    @Test
    void baseFieldsAreFilledAndVersionGoesUp() {
        Branch saved = branches.save(new Branch("test-" + System.nanoTime(), "Test branch", BranchStatus.OPENING));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("system");
        assertThat(saved.getUpdatedBy()).isEqualTo("system");
        assertThat(saved.getVersion()).isZero();

        saved.rename("Test branch renamed");
        Branch updated = branches.saveAndFlush(saved);
        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());
        branches.delete(updated);
    }

    @Test
    void everyTableHasTheBaseFields() {
        List<String> tables = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                  and table_name not in ('flyway_schema_history', 'app_meta')""", String.class);
        assertThat(tables).contains("branch", "app_user", "shop_settings");
        for (String t : tables) {
            List<String> cols = jdbc.queryForList(
                    "select column_name from information_schema.columns where table_schema = 'public' and table_name = ?",
                    String.class, t);
            assertThat(cols).as(t).contains("id", "created_at", "created_by", "updated_at", "updated_by", "version");
        }
    }

    // ---------------------------------------------------------------- rules in the database

    @Test
    void salespersonMustHaveABranch() {
        assertThatThrownBy(() -> new UserAccount("X", "x@y.lk", Role.SALESPERSON, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into app_user (name, email, role, created_at, created_by, updated_at, updated_by)
                values ('X', 'x@y.lk', 'STAFF', now(), 'test', now(), 'test')"""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void emailIsUniqueIgnoringCase() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into app_user (name, email, role, created_at, created_by, updated_at, updated_by)
                values ('Copy', 'OWNER@ceylongemhouse.lk', 'OWNER', now(), 'test', now(), 'test')"""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneShopSettingsRow() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into shop_settings (name, created_at, created_by, updated_at, updated_by)
                values ('Second shop', now(), 'test', now(), 'test')"""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------- money + branch filter on a real table

    @Test
    void moneyIsStoredExactlyAndBranchFilterWorks() {
        Branch ella = branches.findByCode("ella").orElseThrow();
        Branch b2 = branches.findByCode("b2").orElseThrow();
        TestMoneyRecord usd = moneyRecords.save(new TestMoneyRecord(ella, Money.of(new BigDecimal("1500.00"), Currency.USD, new BigDecimal("300.25"))));
        moneyRecords.save(new TestMoneyRecord(b2, Money.lkr("38000")));
        em.clear();

        TestMoneyRecord read = moneyRecords.findById(usd.getId()).orElseThrow();
        assertThat(read.getMoney().getAmountLkr()).isEqualByComparingTo("450375.00");
        assertThat(read.getMoney().getCurrency()).isEqualTo(Currency.USD);
        assertThat(read.getMoney().getRate()).isEqualByComparingTo("300.25");
        assertThat(read.getMoney().getForeignAmount()).isEqualByComparingTo("1500.00");

        long all = moneyRecords.count(BranchSpecifications.inScope(BranchScope.all()));
        long ellaOnly = moneyRecords.count(BranchSpecifications.inScope(BranchScope.of(ella.getId())));
        assertThat(all).isGreaterThanOrEqualTo(2);
        assertThat(ellaOnly).isEqualTo(moneyRecords.findAll().stream().filter(r -> r.getBranch().getId().equals(ella.getId())).count());
        assertThat(ellaOnly).isLessThan(all);
    }
}
