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

    /** Customer brings a gem back: completed sales by invoice number, customer name or gem code / name ('' = latest). */
    @Query("""
            select s from Sale s
            where s.status = lk.nuvi.gemshop.sales.Sale.Status.COMPLETED and (:branch is null or s.branch.id = :branch)
              and (:like = '' or lower(s.number) like :like or lower(s.customer.name) like :like
                   or exists (select 1 from SaleItem i where i.sale = s and (lower(i.gem.code) like :like or lower(i.gem.name) like :like)))
            order by s.soldAt desc, s.id desc""")
    List<Sale> forReturn(Long branch, String like, Pageable page);

    /** Completed sales of a branch (or all) since a moment. */
    @Query("""
            select s from Sale s
            where s.status = lk.nuvi.gemshop.sales.Sale.Status.COMPLETED and s.soldAt >= :from
              and (:branch is null or s.branch.id = :branch)""")
    List<Sale> since(Long branch, Instant from);

    /** Invoices list: a branch (or all), a period, a status (or all), newest first. */
    @Query("""
            select s from Sale s
            where (:branch is null or s.branch.id = :branch)
              and s.soldAt >= :from and s.soldAt < :to
              and (:status is null or s.status = :status)
            order by s.soldAt desc, s.id desc""")
    List<Sale> search(Long branch, Instant from, Instant to, Sale.Status status);

    /** Sales per customer id (for "N previous purchases"). */
    @Query("select s.customer.id, count(s) from Sale s group by s.customer.id")
    List<Object[]> countPerCustomer();

    /** Per customer: live (not cancelled) sales and what they spent, in a branch (or all). Rows: id, count, total. */
    @Query("""
            select s.customer.id, count(s), sum(s.total) from Sale s
            where s.status = lk.nuvi.gemshop.sales.Sale.Status.COMPLETED and (:branch is null or s.branch.id = :branch)
            group by s.customer.id""")
    List<Object[]> spentPerCustomer(Long branch);

    /** One customer's invoices in a branch (or all), newest first. */
    @Query("""
            select s from Sale s
            where s.customer.id = :customer and (:branch is null or s.branch.id = :branch)
            order by s.soldAt desc, s.id desc""")
    List<Sale> ofCustomer(Long customer, Long branch);
}
