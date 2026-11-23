package lk.nuvi.gemshop.commissions;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommissionLineRepository extends JpaRepository<CommissionLine, Long> {

    /** The lines of one sale, oldest first. */
    List<CommissionLine> findBySaleIdOrderByIdAsc(Long saleId);

    List<CommissionLine> findBySaleIdIn(Collection<Long> saleIds);

    /** All lines of a branch (or all branches), newest first. */
    @Query("""
            select l from CommissionLine l
            where :branch is null or l.branch.id = :branch
            order by l.earnedAt desc, l.id desc""")
    List<CommissionLine> list(Long branch);

    /** One guide's lines, newest first. */
    @Query("""
            select l from CommissionLine l
            where l.guide.id = :guide and (:branch is null or l.branch.id = :branch)
            order by l.earnedAt desc, l.id desc""")
    List<CommissionLine> ofGuide(Long guide, Long branch);

    /** All guide lines of a branch (or all) – for the guides list. */
    @Query("""
            select l from CommissionLine l
            where l.payee = lk.nuvi.gemshop.commissions.CommissionLine.Payee.GUIDE and (:branch is null or l.branch.id = :branch)""")
    List<CommissionLine> guideLines(Long branch);

    List<CommissionLine> findByPaymentIdOrderByEarnedAtAsc(Long paymentId);

    long countBySaleId(Long saleId);
}
