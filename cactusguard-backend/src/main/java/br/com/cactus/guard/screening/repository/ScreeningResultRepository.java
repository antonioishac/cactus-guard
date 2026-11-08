package br.com.cactus.guard.screening.repository;

import br.com.cactus.guard.screening.entity.ScreeningResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ScreeningResultRepository extends JpaRepository<ScreeningResult, UUID> {
    List<ScreeningResult> findByPartyId(UUID partyId);
    Page<ScreeningResult> findByStatus(String status, Pageable pageable);
    long countByStatus(String status);

    /** Resultados com a parte carregada (evita N+1 no motor de regras). */
    @Query("SELECT r FROM ScreeningResult r JOIN FETCH r.party WHERE r.status = :status")
    List<ScreeningResult> findByStatusFetchParty(@Param("status") String status);

    List<ScreeningResult> findByPartyIdIn(Collection<UUID> partyIds);
}
