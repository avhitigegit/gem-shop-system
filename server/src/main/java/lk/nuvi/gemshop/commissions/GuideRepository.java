package lk.nuvi.gemshop.commissions;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideRepository extends JpaRepository<Guide, Long> {

    Optional<Guide> findByCode(String code);

    List<Guide> findByActiveTrueOrderByNameAsc();
}
