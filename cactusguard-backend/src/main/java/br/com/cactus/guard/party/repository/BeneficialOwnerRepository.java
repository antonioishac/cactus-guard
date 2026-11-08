package br.com.cactus.guard.party.repository;

import br.com.cactus.guard.party.entity.BeneficialOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BeneficialOwnerRepository extends JpaRepository<BeneficialOwner, UUID> {
    List<BeneficialOwner> findByPartyId(UUID partyId);

    List<BeneficialOwner> findByPartyIdIn(Collection<UUID> partyIds);
    /** owner_document e cifrado (IV aleatorio): a busca e sempre por HMAC (DocumentHash). */
    List<BeneficialOwner> findByOwnerDocumentHash(String ownerDocumentHash);
}
