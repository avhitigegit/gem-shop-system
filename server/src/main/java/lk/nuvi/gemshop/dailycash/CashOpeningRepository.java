package lk.nuvi.gemshop.dailycash;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashOpeningRepository extends JpaRepository<CashOpening, Long> {

    Optional<CashOpening> findByBranchIdAndDay(Long branchId, LocalDate day);

    List<CashOpening> findTop10ByBranchIdOrderByDayDesc(Long branchId);
}
