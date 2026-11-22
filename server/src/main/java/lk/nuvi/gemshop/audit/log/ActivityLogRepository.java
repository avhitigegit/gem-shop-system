package lk.nuvi.gemshop.audit.log;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Add and read only. Deliberately not a JpaRepository: there is no delete or update method to call
 * (and the database refuses them too).
 */
public interface ActivityLogRepository extends Repository<ActivityLogEntry, Long> {

    ActivityLogEntry save(ActivityLogEntry entry);

    long count();

    /**
     * Search for the Activity log screen.
     *
     * @param branchId one branch (rows for the whole shop are always included), or null for all branches
     * @param userId   one person, or null for everybody
     * @param system   true = only automatic entries
     * @param q        lower-case "%text%" matched against action and detail ("%" = everything)
     */
    @Query("""
            select e from ActivityLogEntry e
            where e.at >= :from and e.at < :to
              and (:branchId is null or e.branch is null or e.branch.id = :branchId)
              and (:userId is null or e.user.id = :userId)
              and (:system = false or e.user is null)
              and (lower(e.action) like :q or lower(e.detail) like :q)
            order by e.at desc, e.id desc""")
    Page<ActivityLogEntry> search(@Param("from") Instant from, @Param("to") Instant to, @Param("branchId") Long branchId,
            @Param("userId") Long userId, @Param("system") boolean system, @Param("q") String q, Pageable page);
}
