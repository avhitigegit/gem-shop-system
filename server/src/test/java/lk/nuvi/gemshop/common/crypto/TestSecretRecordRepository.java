package lk.nuvi.gemshop.common.crypto;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestSecretRecordRepository extends JpaRepository<TestSecretRecord, Long> {
    Optional<TestSecretRecord> findByEmailIndex(String emailIndex);
}
