package lk.nuvi.gemshop.stock;

import java.util.List;
import java.util.Optional;
import lk.nuvi.gemshop.gems.Gem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockCorrectionRepository extends JpaRepository<StockCorrection, Long> {

    Optional<StockCorrection> findByNumber(String number);

    boolean existsByGemAndStatus(Gem gem, StockCorrection.Status status);

    /** Corrections of gems in a branch (or all), newest first. */
    @Query("""
            select c from StockCorrection c
            where :branch is null or c.gem.branch.id = :branch
            order by c.requestedAt desc, c.id desc""")
    List<StockCorrection> forBranch(Long branch);
}
