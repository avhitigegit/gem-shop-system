package lk.nuvi.gemshop.audit.alert;

import java.time.Instant;
import java.util.Collection;
import lk.nuvi.gemshop.auth.user.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    /**
     * @param branchId one branch (whole-shop alerts always included), or null for all branches
     * @param types    the types of one filter group
     */
    @Query("""
            select a from Alert a
            where (:branchId is null or a.branch is null or a.branch.id = :branchId)
              and a.type in :types
              and (:unreadOnly = false or a.readAt is null)
            order by a.at desc, a.id desc""")
    Page<Alert> search(@Param("branchId") Long branchId, @Param("types") Collection<AlertType> types,
            @Param("unreadOnly") boolean unreadOnly, Pageable page);

    @Query("""
            select count(a) from Alert a
            where a.readAt is null and (:branchId is null or a.branch is null or a.branch.id = :branchId)""")
    long countUnread(@Param("branchId") Long branchId);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Alert a set a.readAt = :at, a.readBy = :user, a.updatedAt = :at, a.updatedBy = :by, a.version = a.version + 1
            where a.readAt is null and (:branchId is null or a.branch is null or a.branch.id = :branchId)""")
    int markAllRead(@Param("branchId") Long branchId, @Param("user") UserAccount user, @Param("by") String by,
            @Param("at") Instant at);
}
