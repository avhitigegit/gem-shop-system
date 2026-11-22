package lk.nuvi.gemshop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestMoneyRecordRepository
        extends JpaRepository<TestMoneyRecord, Long>, JpaSpecificationExecutor<TestMoneyRecord> {}
