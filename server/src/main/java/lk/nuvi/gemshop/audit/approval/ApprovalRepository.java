package lk.nuvi.gemshop.audit.approval;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    Optional<Approval> findByNumber(String number);

    List<Approval> findByNumberIn(java.util.Collection<String> numbers);

    /**
     * Requests a person may see.
     *
     * @param branchId the branch in scope (whole-shop requests always included), or null for all branches
     * @param userId   also the person's own requests (salesperson / staff), or null
     */
    @Query("""
            select a from Approval a
            where (:branchId is null or a.branch is null or a.branch.id = :branchId or a.requestedBy.id = :userId)
              and a.status in :statuses
              and a.type in :types
            order by a.requestedAt desc, a.id desc""")
    Page<Approval> search(@Param("branchId") Long branchId, @Param("userId") Long userId,
            @Param("statuses") Collection<ApprovalStatus> statuses, @Param("types") Collection<ApprovalType> types,
            Pageable page);

    @Query("""
            select count(a) from Approval a
            where a.status = lk.nuvi.gemshop.audit.approval.ApprovalStatus.PENDING
              and (:branchId is null or a.branch is null or a.branch.id = :branchId or a.requestedBy.id = :userId)""")
    long countPending(@Param("branchId") Long branchId, @Param("userId") Long userId);

    @Query(value = "select nextval('approval_no_seq')", nativeQuery = true)
    long nextNumber();

    /** Sample data: continue the numbers after the prototype's last request. */
    @Query(value = "select setval('approval_no_seq', :n)", nativeQuery = true)
    long setNextNumberAfter(@Param("n") long n);
}
