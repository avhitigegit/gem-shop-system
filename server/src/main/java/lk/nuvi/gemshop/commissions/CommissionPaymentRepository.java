package lk.nuvi.gemshop.commissions;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionPaymentRepository extends JpaRepository<CommissionPayment, Long> {

    Optional<CommissionPayment> findByNumber(String number);
}
