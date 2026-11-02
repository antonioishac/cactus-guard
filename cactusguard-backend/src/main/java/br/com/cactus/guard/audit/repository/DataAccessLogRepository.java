package br.com.cactus.guard.audit.repository;

import br.com.cactus.guard.audit.entity.DataAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DataAccessLogRepository extends JpaRepository<DataAccessLog, UUID> {
    long deleteByCreatedAtBefore(Instant cutoff);
    Page<DataAccessLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
