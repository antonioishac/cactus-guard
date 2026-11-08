package br.com.cactus.guard.alert.repository;

import br.com.cactus.guard.alert.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Page<Alert> findByStatus(String status, Pageable pageable);

    Page<Alert> findBySeverity(String severity, Pageable pageable);

    @Query("SELECT a FROM Alert a WHERE a.party.id = :partyId")
    Page<Alert> findByPartyId(@Param("partyId") UUID partyId, Pageable pageable);

    boolean existsByDedupKey(String dedupKey);

    /** Chaves ja existentes (qualquer status) dentre as informadas: evita N consultas por parte. */
    @Query("SELECT a.dedupKey FROM Alert a WHERE a.dedupKey IN :keys")
    List<String> findExistingDedupKeys(@Param("keys") Collection<String> keys);

    long countByPartyIdAndRuleCodeAndStatus(UUID partyId, String ruleCode, String status);

    long countByStatus(String status);
    long countBySeverityAndStatus(String severity, String status);
}
