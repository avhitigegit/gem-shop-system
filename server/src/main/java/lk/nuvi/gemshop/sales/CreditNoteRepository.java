package lk.nuvi.gemshop.sales;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    Optional<CreditNote> findByNumber(String number);

    /** Credit notes of a branch (or all), newest first. */
    @Query("""
            select c from CreditNote c
            where (:branch is null or c.saleReturn.branch.id = :branch)
            order by c.issuedAt desc, c.id desc""")
    List<CreditNote> list(Long branch);

    /** Refund credit notes of a branch not paid back from the drawer yet, oldest first (P3-06). */
    @Query("""
            select c from CreditNote c
            where c.saleReturn.kind = lk.nuvi.gemshop.sales.SaleReturn.Kind.REFUND and c.cashRefundAt is null
              and c.saleReturn.branch.id = :branch
            order by c.issuedAt""")
    List<CreditNote> refundsToPay(Long branch);

    /** Refunds paid from a branch's drawer in a period. */
    @Query("""
            select c from CreditNote c
            where c.cashRefundBranch.id = :branch and c.cashRefundAt >= :from and c.cashRefundAt < :to
            order by c.cashRefundAt""")
    List<CreditNote> refundedFromDrawer(Long branch, java.time.Instant from, java.time.Instant to);
}
