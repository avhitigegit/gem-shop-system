package lk.nuvi.gemshop.commissions;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommissionPaymentRepository extends JpaRepository<CommissionPayment, Long> {

    Optional<CommissionPayment> findByNumber(String number);

    /** Commission paid "Cash (drawer)" from a branch's drawer on a day (P3-06: comes off the expected cash). */
    @Query("""
            select p from CommissionPayment p
            where p.method = lk.nuvi.gemshop.commissions.PayMethod.DRAWER and p.branch.id = :branch and p.paidOn = :day
            order by p.paidAt""")
    List<CommissionPayment> fromDrawer(Long branch, LocalDate day);
}
