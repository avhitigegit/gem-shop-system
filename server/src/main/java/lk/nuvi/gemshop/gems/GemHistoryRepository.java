package lk.nuvi.gemshop.gems;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GemHistoryRepository extends JpaRepository<GemHistory, Long> {

    List<GemHistory> findByGemOrderByAtAscIdAsc(Gem gem);
}
