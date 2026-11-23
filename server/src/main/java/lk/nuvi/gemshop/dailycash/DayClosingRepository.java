package lk.nuvi.gemshop.dailycash;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DayClosingRepository extends JpaRepository<DayClosing, Long> {

    Optional<DayClosing> findByNumber(String number);

    Optional<DayClosing> findByBranchIdAndDay(Long branchId, LocalDate day);

    boolean existsByBranchIdAndDay(Long branchId, LocalDate day);

    /** The branch's latest closing before a day (its counted cash is the next morning's suggestion). */
    Optional<DayClosing> findFirstByBranchIdAndDayBeforeOrderByDayDesc(Long branchId, LocalDate day);

    /** Closings of a branch (or all) since a day, newest first. */
    @Query("""
            select c from DayClosing c
            where c.day >= :since and (:branch is null or c.branch.id = :branch)
            order by c.day desc, c.id desc""")
    List<DayClosing> since(Long branch, LocalDate since);
}
