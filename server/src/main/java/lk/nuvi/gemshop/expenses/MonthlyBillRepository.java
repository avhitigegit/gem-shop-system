package lk.nuvi.gemshop.expenses;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MonthlyBillRepository extends JpaRepository<MonthlyBill, Long> {

    Optional<MonthlyBill> findByCode(String code);

    List<MonthlyBill> findAllByOrderByDayAscCodeAsc();

    long countByExpenseType(String expenseType);

    @Modifying
    @Query("update MonthlyBill b set b.expenseType = :to where b.expenseType = :from")
    int renameType(String from, String to);
}
