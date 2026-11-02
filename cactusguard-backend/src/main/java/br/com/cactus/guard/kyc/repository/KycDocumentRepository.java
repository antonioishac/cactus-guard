package br.com.cactus.guard.kyc.repository;

import br.com.cactus.guard.kyc.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {
    List<KycDocument> findByProcessId(UUID processId);
}
