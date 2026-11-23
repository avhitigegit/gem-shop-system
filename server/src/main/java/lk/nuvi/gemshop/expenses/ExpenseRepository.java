package lk.nuvi.gemshop.expenses;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByNumber(String number);

    /** Expenses paid in cash from a branch's drawer in a period (not the rejected ones), oldest first. */
    @Query("""
            select e from Expense e
            where e.branch.id = :branch and e.method = lk.nuvi.gemshop.expenses.Expense.Method.DRAWER
              and e.status <> lk.nuvi.gemshop.expenses.Expense.Status.REJECTED
              and e.spentAt >= :from and e.spentAt < :to
            order by e.spentAt, e.id""")
    List<Expense> fromDrawer(Long branch, Instant from, Instant to);

    /** Expenses waiting for the owner in a branch (or all). */
    @Query("""
            select count(e) from Expense e
            where e.status = lk.nuvi.gemshop.expenses.Expense.Status.PENDING and (:branch is null or e.branch.id = :branch)""")
    long countWaiting(Long branch);

    /** Expenses in a period (a branch or all), newest first. */
    @Query("""
            select e from Expense e
            where (:branch is null or e.branch.id = :branch) and e.spentAt >= :from and e.spentAt < :to
            order by e.spentAt desc, e.id desc""")
    List<Expense> between(Long branch, Instant from, Instant to);

    @Query("select e from Expense e where (:branch is null or e.branch.id = :branch) order by e.spentAt desc, e.id desc")
    List<Expense> all(Long branch);

    @Query("select count(e) > 0 from Expense e where e.monthlyBill = :bill and e.spentAt >= :from and e.spentAt < :to")
    boolean addedFor(MonthlyBill bill, Instant from, Instant to);

    long countByExpenseType(String expenseType);

    @org.springframework.data.jpa.repository.Modifying
    @Query("update Expense e set e.expenseType = :to where e.expenseType = :from")
    int renameType(String from, String to);
}
