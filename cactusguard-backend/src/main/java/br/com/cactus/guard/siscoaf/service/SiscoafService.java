package br.com.cactus.guard.siscoaf.service;

import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.integration.siscoaf.SiscoafOrchestrator;
import br.com.cactus.guard.siscoaf.dto.SiscoafResponse;
import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import br.com.cactus.guard.siscoaf.repository.SiscoafCommunicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SiscoafService {

    private final SiscoafCommunicationRepository repository;
    private final CaseRepository caseRepository;
    private final SiscoafOrchestrator orchestrator;

    @Transactional
    public SiscoafResponse createDraft(UUID caseId, String type, Map<String, Object> payload) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));

        SiscoafCommunication comm = SiscoafCommunication.builder()
                .caseEntity(caseEntity)
                .party(caseEntity.getParty())
                .type(type)
                .status("DRAFT")
                .payload(payload)
                .build();

        comm = repository.save(comm);
        return toResponse(comm);
    }

    @Transactional
    public SiscoafResponse submit(UUID id) {
        SiscoafCommunication comm = repository.findById(id)
                .orElseThrow(() -> new br.com.cactus.guard.common.exception.BusinessException("Communication not found"));

        if (!"APPROVED".equals(comm.getStatus()) && !"DRAFT".equals(comm.getStatus())) {
            throw new br.com.cactus.guard.common.exception.BusinessException("Cannot submit in status: " + comm.getStatus());
        }

        comm = orchestrator.submit(comm);
        return toResponse(comm);
    }

    @Transactional(readOnly = true)
    public Page<SiscoafResponse> findByStatus(String status, Pageable pageable) {
        return repository.findByStatus(status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SiscoafResponse findById(UUID id) {
        SiscoafCommunication comm = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Communication not found"));
        return toResponse(comm);
    }

    private SiscoafResponse toResponse(SiscoafCommunication c) {
        return SiscoafResponse.builder()
                .id(c.getId())
                .caseId(c.getCaseEntity() != null ? c.getCaseEntity().getId() : null)
                .partyId(c.getParty() != null ? c.getParty().getId() : null)
                .type(c.getType())
                .status(c.getStatus())
                .protocolNumber(c.getProtocolNumber())
                .submittedAt(c.getSubmittedAt())
                .payload(c.getPayload())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
