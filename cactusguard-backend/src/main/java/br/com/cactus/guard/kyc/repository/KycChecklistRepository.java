package br.com.cactus.guard.kyc.repository;

import br.com.cactus.guard.kyc.entity.KycChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface KycChecklistRepository extends JpaRepository<KycChecklistItem, UUID> {
    List<KycChecklistItem> findByProcessId(UUID processId);
    long countByProcessIdAndRequiredTrueAndCompletedFalse(UUID processId);
}
