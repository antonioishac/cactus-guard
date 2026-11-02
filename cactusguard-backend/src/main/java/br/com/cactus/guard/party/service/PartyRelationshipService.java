package br.com.cactus.guard.party.service;

import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.entity.PartyRelationship;
import br.com.cactus.guard.party.repository.PartyRelationshipRepository;
import br.com.cactus.guard.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartyRelationshipService {

    private final PartyRelationshipRepository repository;
    private final PartyRepository partyRepository;
    private final DataAccessService dataAccessService;

    @Transactional
    public PartyRelationship add(UUID partyId, UUID relatedPartyId, String relationshipType, String description) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException("Parte não encontrada"));
        Party related = partyRepository.findById(relatedPartyId)
                .orElseThrow(() -> new BusinessException("Parte relacionada não encontrada"));

        PartyRelationship rel = PartyRelationship.builder()
                .party(party)
                .relatedParty(related)
                .relationshipType(relationshipType)
                .description(description)
                .build();

        return repository.save(rel);
    }

    public List<PartyRelationship> findByParty(UUID partyId) {
        dataAccessService.log("PARTY_RELATIONSHIP", partyId, "relationshipType,relatedParty", "CONSULTA", null);
        return repository.findByPartyIdAndActiveTrue(partyId);
    }

    @Transactional
    public void deactivate(UUID id) {
        PartyRelationship rel = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Relacionamento não encontrado"));
        rel.setActive(false);
        repository.save(rel);
    }
}
