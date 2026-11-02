package br.com.cactus.guard.alert.repository;

import br.com.cactus.guard.alert.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Page<Alert> findByStatus(String status, Pageable pageable);

    Page<Alert> findBySeverity(String severity, Pageable pageable);

    @Query("SELECT a FROM Alert a WHERE a.party.id = :partyId")
    Page<Alert> findByPartyId(@Param("partyId") UUID partyId, Pageable pageable);

    long countByStatus(String status);
    long countBySeverityAndStatus(String severity, String status);
}
