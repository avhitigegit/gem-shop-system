package lk.nuvi.gemshop.sales;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByNumber(String number);

    /** Newest sales of a branch (or all branches). */
    @Query("""
            select s from Sale s
            where :branch is null or s.branch.id = :branch
            order by s.soldAt desc, s.id desc""")
    List<Sale> latest(Long branch, Pageable page);

    /** Completed sales of a branch (or all) since a moment. */
    @Query("""
            select s from Sale s
            where s.status = lk.nuvi.gemshop.sales.Sale.Status.COMPLETED and s.soldAt >= :from
              and (:branch is null or s.branch.id = :branch)""")
    List<Sale> since(Long branch, Instant from);

    /** Sales per customer id (for "N previous purchases"). */
    @Query("select s.customer.id, count(s) from Sale s group by s.customer.id")
    List<Object[]> countPerCustomer();
}
