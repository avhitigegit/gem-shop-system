package lk.nuvi.gemshop.stock;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByNumber(String number);

    /** Transfers from or to a branch (or all), newest first. */
    @Query("""
            select t from Transfer t
            where :branch is null or t.from.id = :branch or t.to.id = :branch
            order by t.sentAt desc, t.id desc""")
    List<Transfer> forBranch(Long branch);

    /** Transfers still on the way to a branch (or to any branch). */
    @Query("""
            select count(t) from Transfer t
            where t.status = lk.nuvi.gemshop.stock.Transfer.Status.IN_TRANSIT and (:branch is null or t.to.id = :branch)""")
    long onTheWayTo(Long branch);
}
