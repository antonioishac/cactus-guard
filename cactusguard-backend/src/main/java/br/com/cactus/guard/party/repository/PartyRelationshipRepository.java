package br.com.cactus.guard.party.repository;

import br.com.cactus.guard.party.entity.PartyRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PartyRelationshipRepository extends JpaRepository<PartyRelationship, UUID> {
    List<PartyRelationship> findByPartyIdAndActiveTrue(UUID partyId);
    List<PartyRelationship> findByRelatedPartyIdAndActiveTrue(UUID relatedPartyId);
}
