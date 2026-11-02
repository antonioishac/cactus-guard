package br.com.cactus.guard.party.repository;

import br.com.cactus.guard.party.entity.BeneficialOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BeneficialOwnerRepository extends JpaRepository<BeneficialOwner, UUID> {
    List<BeneficialOwner> findByPartyId(UUID partyId);
    List<BeneficialOwner> findByOwnerDocument(String document);
}
