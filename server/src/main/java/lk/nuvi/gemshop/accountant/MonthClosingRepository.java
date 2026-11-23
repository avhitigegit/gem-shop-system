package lk.nuvi.gemshop.accountant;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthClosingRepository extends JpaRepository<MonthClosing, Long> {

    Optional<MonthClosing> findByMonth(String month);

    boolean existsByMonthAndStatus(String month, MonthClosing.Status status);
}
