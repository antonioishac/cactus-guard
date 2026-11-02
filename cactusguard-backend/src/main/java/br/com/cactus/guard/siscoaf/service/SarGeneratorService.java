package br.com.cactus.guard.siscoaf.service;

import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import br.com.cactus.guard.siscoaf.repository.SiscoafCommunicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SarGeneratorService {

    private final CaseRepository caseRepository;
    private final SiscoafCommunicationRepository siscoafRepository;

    @Transactional
    public SiscoafCommunication generateSar(UUID caseId) {
        Case c = caseRepository.findById(caseId)
                .orElseThrow(() -> new BusinessException("Case não encontrado"));

        if (!"REJECTED".equals(c.getStatus()) && !"ESCALATED".equals(c.getStatus())) {
            throw new BusinessException("SAR só pode ser gerado para cases rejeitados ou escalados");
        }

        Map<String, Object> payload = Map.of(
                "caseId", c.getId().toString(),
                "partyName", c.getParty() != null ? c.getParty().getName() : "",
                "partyDocument", c.getParty() != null ? c.getParty().getDocument() : "",
                "decision", c.getDecision() != null ? c.getDecision() : "",
                "justification", c.getDecisionJustification() != null ? c.getDecisionJustification() : "",
                "alertType", c.getAlert() != null ? c.getAlert().getType() : "",
                "severity", c.getAlert() != null ? c.getAlert().getSeverity() : ""
        );

        SiscoafCommunication comm = SiscoafCommunication.builder()
                .caseEntity(c)
                .type("SAR")
                .status("DRAFT")
                .payload(payload)
                .build();

        return siscoafRepository.save(comm);
    }
}
