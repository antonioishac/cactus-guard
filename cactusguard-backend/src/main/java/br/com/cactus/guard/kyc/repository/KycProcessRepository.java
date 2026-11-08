package br.com.cactus.guard.kyc.repository;
import java.time.Instant;
import java.util.List;

import br.com.cactus.guard.kyc.entity.KycProcess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface KycProcessRepository extends JpaRepository<KycProcess, UUID> {
    Page<KycProcess> findByStatus(String status, Pageable pageable);
    Page<KycProcess> findByPartyId(UUID partyId, Pageable pageable);
    Optional<KycProcess> findFirstByPartyIdAndStatusOrderByCreatedAtDesc(UUID partyId, String status);
    List<KycProcess> findByStatusAndExpiresAtBefore(String status, Instant now);

    long countByStatus(String status);
}
