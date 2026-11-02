package br.com.cactus.guard.screening.repository;

import br.com.cactus.guard.screening.entity.ScreeningResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ScreeningResultRepository extends JpaRepository<ScreeningResult, UUID> {
    List<ScreeningResult> findByPartyId(UUID partyId);
    Page<ScreeningResult> findByStatus(String status, Pageable pageable);
    long countByStatus(String status);
}
