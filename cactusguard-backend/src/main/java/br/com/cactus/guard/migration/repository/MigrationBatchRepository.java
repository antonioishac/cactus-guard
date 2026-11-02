package br.com.cactus.guard.migration.repository;

import br.com.cactus.guard.migration.entity.MigrationBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MigrationBatchRepository extends JpaRepository<MigrationBatch, UUID> {
}
