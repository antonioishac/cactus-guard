package br.com.cactus.guard.party.service;

import br.com.cactus.guard.alert.service.AlertCreationService;
import br.com.cactus.guard.audit.service.DataAccessService;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.util.DocumentHash;
import br.com.cactus.guard.party.dto.BeneficialOwnerResponse;
import br.com.cactus.guard.party.entity.BeneficialOwner;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.BeneficialOwnerRepository;
import br.com.cactus.guard.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BeneficialOwnerService {

    private final BeneficialOwnerRepository repository;
    private final PartyRepository partyRepository;
    private final AlertCreationService alertCreation;
    private final DataAccessService dataAccessService;

    @Transactional
    public BeneficialOwnerResponse addResponse(UUID partyId, String document, String name, BigDecimal ownershipPct,
                                               String controlType, boolean isPep, String ownerType) {
        return BeneficialOwnerResponse.from(add(partyId, document, name, ownershipPct, controlType, isPep, ownerType));
    }

    @Transactional
    public BeneficialOwner add(UUID partyId, String document, String name, BigDecimal ownershipPct, String controlType, boolean isPep) {
        return add(partyId, document, name, ownershipPct, controlType, isPep, null);
    }

    @Transactional
    public BeneficialOwner add(UUID partyId, String document, String name, BigDecimal ownershipPct, String controlType,
                               boolean isPep, String ownerType) {
        if (document == null || document.isBlank()) {
            throw new IllegalArgumentException("document: obrigatório");
        }
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException("Parte não encontrada"));

        BeneficialOwner owner = BeneficialOwner.builder()
                .party(party)
                .ownerDocument(document)
                .ownerDocumentHash(DocumentHash.of(document))
                .ownerName(name)
                .ownershipPct(ownershipPct)
                .controlType(controlType)
                .isPep(isPep)
                .ownerType(ownerType)
                .build();
        // vincula a parte cadastrada correspondente (permite navegar para a ficha), por HMAC do documento
        Party linked = partyRepository.findByDocumentHash(owner.getOwnerDocumentHash()).orElse(null);
        if (linked != null) {
            owner.setOwnerParty(linked);
            if (owner.getOwnerType() == null) owner.setOwnerType(linked.getType());
        }

        owner = repository.save(owner);

        if (isPep) {
            alertCreation.createIfAbsent(new AlertCreationService.AlertDraft(party, null, "UBO_PEP", "HIGH",
                    "UBO identificado como PEP",
                    "Beneficiário final " + name + " marcado como PEP",
                    "UBO_PEP", "UBO_PEP:" + party.getId() + ":" + owner.getId(),
                    null, null, java.util.Map.of("beneficialOwnerId", owner.getId().toString()), List.of()));
        }

        return owner;
    }

    @Transactional(readOnly = true)
    public List<BeneficialOwnerResponse> findByParty(UUID partyId) {
        dataAccessService.log("BENEFICIAL_OWNER", partyId, "ownerDocument,ownerName", "CONSULTA", null);
        return repository.findByPartyId(partyId).stream().map(BeneficialOwnerResponse::from).toList();
    }

    @Transactional
    public void remove(UUID id) {
        repository.deleteById(id);
    }
}
