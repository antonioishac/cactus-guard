package br.com.cactus.guard.risk.repository;

import br.com.cactus.guard.risk.entity.RiskHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RiskHistoryRepository extends JpaRepository<RiskHistory, UUID> {
    Page<RiskHistory> findByPartyIdOrderByCreatedAtDesc(UUID partyId, Pageable pageable);
}
