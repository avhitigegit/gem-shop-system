package lk.nuvi.gemshop.sales;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    Optional<Quote> findByNumber(String number);

    /** Reservations or quotations of a branch (or all), newest first. */
    @Query("""
            select q from Quote q
            where q.kind = :kind and (:branch is null or q.branch.id = :branch)
            order by q.createdOn desc, q.id desc""")
    List<Quote> list(Quote.Kind kind, Long branch);

    /** Active ones whose expiry date has passed (the daily job). */
    @Query("select q from Quote q where q.status = lk.nuvi.gemshop.sales.Quote.Status.ACTIVE and q.expires < :today")
    List<Quote> due(LocalDate today);

    /** The active reservation that keeps a gem, if any. */
    @Query("""
            select q from Quote q join q.items i
            where i.gem.id = :gemId and q.kind = lk.nuvi.gemshop.sales.Quote.Kind.RESERVATION
              and q.status = lk.nuvi.gemshop.sales.Quote.Status.ACTIVE""")
    List<Quote> keeping(Long gemId);

    @Query("""
            select count(q) from Quote q
            where q.kind = :kind and q.status = lk.nuvi.gemshop.sales.Quote.Status.ACTIVE
              and (:branch is null or q.branch.id = :branch)""")
    long active(Quote.Kind kind, Long branch);

    /** One customer's reservations and quotations in a branch (or all), newest first. */
    @Query("""
            select q from Quote q
            where q.customer.id = :customer and (:branch is null or q.branch.id = :branch)
            order by q.createdOn desc, q.id desc""")
    List<Quote> ofCustomer(Long customer, Long branch);
}
