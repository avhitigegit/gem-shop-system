package lk.nuvi.gemshop.settings.commission;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionRulesRepository extends JpaRepository<CommissionRules, Long> {

    /** There is only one row. */
    default Optional<CommissionRules> current() {
        return findAll().stream().findFirst();
    }
}
