package br.com.cactus.guard.party.service;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.audit.service.DataAccessService;
import br.com.cactus.guard.common.exception.BusinessException;
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
    private final AlertRepository alertRepository;
    private final DataAccessService dataAccessService;

    @Transactional
    public BeneficialOwner add(UUID partyId, String document, String name, BigDecimal ownershipPct, String controlType, boolean isPep) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException("Parte não encontrada"));

        BeneficialOwner owner = BeneficialOwner.builder()
                .party(party)
                .ownerDocument(document)
                .ownerName(name)
                .ownershipPct(ownershipPct)
                .controlType(controlType)
                .isPep(isPep)
                .build();

        owner = repository.save(owner);

        if (isPep) {
            Alert alert = Alert.builder()
                    .party(party)
                    .type("UBO_PEP")
                    .severity("HIGH")
                    .status("OPEN")
                    .title("UBO identificado como PEP")
                    .description("Beneficiário final " + name + " (" + document + ") marcado como PEP")
                    .ruleCode("UBO_PEP")
                    .build();
            alertRepository.save(alert);
        }

        return owner;
    }

    public List<BeneficialOwner> findByParty(UUID partyId) {
        dataAccessService.log("BENEFICIAL_OWNER", partyId, "ownerDocument,ownerName", "CONSULTA", null);
        return repository.findByPartyId(partyId);
    }

    @Transactional
    public void remove(UUID id) {
        repository.deleteById(id);
    }
}
