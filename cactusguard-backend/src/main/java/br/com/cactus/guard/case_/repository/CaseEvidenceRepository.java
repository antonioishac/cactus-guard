package br.com.cactus.guard.case_.repository;

import br.com.cactus.guard.case_.entity.CaseEvidence;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CaseEvidenceRepository extends JpaRepository<CaseEvidence, UUID> {
    @EntityGraph(attributePaths = "uploadedBy")
    List<CaseEvidence> findByCaseEntityIdOrderByCreatedAtAsc(UUID caseId);
    boolean existsByCaseEntityIdAndSha256(UUID caseId, String sha256);
}
