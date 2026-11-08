package br.com.cactus.guard.case_.repository;

import br.com.cactus.guard.case_.entity.Case;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, UUID>, JpaSpecificationExecutor<Case> {
    Page<Case> findByStatus(String status, Pageable pageable);
    Page<Case> findByAssignedToId(UUID assignedToId, Pageable pageable);
    Page<Case> findByAssignedToIsNull(Pageable pageable);
    long countByStatus(String status);
    long countByDecidedAtIsNull();

    /** Listagem filtrada com parte e responsaveis carregados. */
    @Override
    @EntityGraph(attributePaths = {"party", "assignedTo", "alert", "decidedBy", "approvedBy"})
    Page<Case> findAll(Specification<Case> spec, Pageable pageable);

    /** Cases com decisao COMMUNICATE das partes informadas ("alertas confirmados"). */
    List<Case> findByPartyIdInAndDecision(Collection<UUID> partyIds, String decision);

    // ---------------------------------------------------------------- SLA agregado (dashboard)

    long countByStatusIn(Collection<String> statuses);

    long countByStatusInAndAnalysisDeadlineBefore(Collection<String> statuses, Instant limit);

    long countByStatusInAndAnalysisDeadlineBetween(Collection<String> statuses, Instant from, Instant to);

    long countByStatusAndCommunicationDeadlineBefore(String status, Instant limit);

    long countByStatusAndCommunicationDeadlineBetween(String status, Instant from, Instant to);
}
