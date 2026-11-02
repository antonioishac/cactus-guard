package br.com.cactus.guard.migration.repository;

import br.com.cactus.guard.migration.entity.MigrationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MigrationRecordRepository extends JpaRepository<MigrationRecord, UUID> {
    List<MigrationRecord> findByBatchIdAndStatus(UUID batchId, String status);
    Page<MigrationRecord> findByBatchId(UUID batchId, Pageable pageable);
    long countByBatchIdAndStatus(UUID batchId, String status);
}
