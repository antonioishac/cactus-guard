package br.com.cactus.guard.case_.repository;

import br.com.cactus.guard.case_.entity.Case;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, UUID> {
    Page<Case> findByStatus(String status, Pageable pageable);
    Page<Case> findByAssignedToId(UUID assignedToId, Pageable pageable);
    long countByStatus(String status);
}
