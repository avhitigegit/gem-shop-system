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
}
