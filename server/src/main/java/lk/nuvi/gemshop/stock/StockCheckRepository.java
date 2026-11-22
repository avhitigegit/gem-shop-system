package lk.nuvi.gemshop.stock;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockCheckRepository extends JpaRepository<StockCheck, Long> {

    @Query("select c from StockCheck c where :branch is null or c.branch.id = :branch order by c.checkedAt desc, c.id desc")
    List<StockCheck> forBranch(Long branch);
}
