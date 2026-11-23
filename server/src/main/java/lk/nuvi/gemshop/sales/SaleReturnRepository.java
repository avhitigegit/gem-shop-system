package lk.nuvi.gemshop.sales;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {

    Optional<SaleReturn> findByNumber(String number);

    /** Returns of a branch (or all), newest first. */
    @Query("""
            select r from SaleReturn r
            where (:branch is null or r.branch.id = :branch)
            order by r.requestedAt desc, r.id desc""")
    List<SaleReturn> list(Long branch);

    /** All returns of one sale (which gems already came back or wait for the owner). */
    List<SaleReturn> findBySaleId(Long saleId);

    @Query("""
            select r from SaleReturn r
            where r.status = lk.nuvi.gemshop.sales.SaleReturn.Status.PENDING and (:branch is null or r.branch.id = :branch)
            order by r.requestedAt""")
    List<SaleReturn> waiting(Long branch);
}
