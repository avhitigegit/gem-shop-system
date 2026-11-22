package lk.nuvi.gemshop.sales;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HeldSaleRepository extends JpaRepository<HeldSale, Long> {

    Optional<HeldSale> findByNumber(String number);

    Optional<HeldSale> findByApprovalNo(String approvalNo);

    /** Held sales in the given states for a branch (or all), newest first. */
    @Query("""
            select h from HeldSale h
            where h.status in :states and (:branch is null or h.branch.id = :branch)
            order by h.heldAt desc, h.id desc""")
    List<HeldSale> inStates(Long branch, Collection<HeldSale.Status> states);
}
