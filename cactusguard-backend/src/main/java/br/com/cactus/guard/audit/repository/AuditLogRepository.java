package br.com.cactus.guard.audit.repository;

import br.com.cactus.guard.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);
    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);
    long deleteByCreatedAtBefore(Instant cutoff);
}
