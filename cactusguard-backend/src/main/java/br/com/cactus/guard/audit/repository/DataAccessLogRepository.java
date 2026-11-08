package br.com.cactus.guard.audit.repository;

import java.time.Instant;

import br.com.cactus.guard.audit.entity.DataAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;

public interface DataAccessLogRepository extends JpaRepository<DataAccessLog, UUID>, JpaSpecificationExecutor<DataAccessLog> {
    Page<DataAccessLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId, Pageable pageable);

    long deleteByCreatedAtBefore(Instant cutoff);
    Page<DataAccessLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
