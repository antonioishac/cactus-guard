package br.com.cactus.guard.siscoaf.repository;

import br.com.cactus.guard.siscoaf.entity.SiscoafHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SiscoafHistoryRepository extends JpaRepository<SiscoafHistory, UUID> {
    @EntityGraph(attributePaths = "performedBy")
    List<SiscoafHistory> findByCommunicationIdOrderByCreatedAtAsc(UUID communicationId);
}
