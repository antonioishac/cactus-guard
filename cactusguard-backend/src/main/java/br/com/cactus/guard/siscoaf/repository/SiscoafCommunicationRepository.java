package br.com.cactus.guard.siscoaf.repository;

import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SiscoafCommunicationRepository extends JpaRepository<SiscoafCommunication, UUID> {
    Page<SiscoafCommunication> findByStatus(String status, Pageable pageable);
    Page<SiscoafCommunication> findByCaseEntityId(UUID caseId, Pageable pageable);
}
