package lk.nuvi.gemshop.settings.commission;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CommissionLevelRepository extends JpaRepository<CommissionLevel, Long> {

    List<CommissionLevel> findAllByOrderByLevelNoAsc();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CommissionLevel l")
    void deleteAllLevels();
}
