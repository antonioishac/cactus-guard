package br.com.cactus.guard.kyc.service;

import br.com.cactus.guard.integration.kyc.KycOrchestrator;
import br.com.cactus.guard.kyc.dto.KycProcessResponse;
import br.com.cactus.guard.kyc.entity.KycProcess;
import br.com.cactus.guard.kyc.repository.KycChecklistRepository;
import br.com.cactus.guard.kyc.repository.KycProcessRepository;
import br.com.cactus.guard.notification.service.NotificationService;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycService {

    private final KycProcessRepository kycRepository;
    private final KycChecklistRepository checklistRepository;
    private final PartyRepository partyRepository;
    private final NotificationService notificationService;
    private final KycOrchestrator orchestrator;

    @Transactional
    public KycProcessResponse startOnboarding(UUID partyId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("Party not found"));

        KycProcess process = KycProcess.builder()
                .party(party)
                .type("ONBOARDING")
                .status("IN_PROGRESS")
                .startedAt(Instant.now())
                .expiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
                .build();

        process = kycRepository.save(process);

        // Notify is handled by the assigned user if present
        return toResponse(process);
    }

    @Transactional
    public KycProcessResponse approve(UUID processId) {
        KycProcess process = kycRepository.findById(processId)
                .orElseThrow(() -> new br.com.cactus.guard.common.exception.BusinessException("KYC process not found"));

        long incompleteRequired = checklistRepository.countByProcessIdAndRequiredTrueAndCompletedFalse(processId);
        if (incompleteRequired > 0) {
            throw new br.com.cactus.guard.common.exception.BusinessException(
                    "Não é possível aprovar: " + incompleteRequired + " item(ns) obrigatório(s) incompleto(s)");
        }

        br.com.cactus.guard.party.entity.Party partyToApprove = process.getParty();
        if (partyToApprove != null && partyToApprove.isPep() && !"EXPIRED".equals(partyToApprove.getPepStatus())
                && partyToApprove.getPepApprovalBy() == null) {
            throw new br.com.cactus.guard.common.exception.BusinessException(
                    "Relacionamento com PEP exige aprovação prévia da alta administração (aprovação de PEP pendente)");
        }

        orchestrator.validateDocument(process);

        process.setStatus("APPROVED");
        process.setCompletedAt(Instant.now());
        process = kycRepository.save(process);

        return toResponse(process);
    }

    @Transactional
    public KycProcessResponse reject(UUID processId) {
        KycProcess process = kycRepository.findById(processId)
                .orElseThrow(() -> new IllegalArgumentException("KYC process not found"));

        process.setStatus("REJECTED");
        process.setCompletedAt(Instant.now());
        process = kycRepository.save(process);

        // Notify is handled by the assigned user if present
        return toResponse(process);
    }

    @Transactional(readOnly = true)
    public Page<KycProcessResponse> findByParty(UUID partyId, String status, Pageable pageable) {
        Page<KycProcess> page = (status == null || status.isBlank())
                ? kycRepository.findByPartyId(partyId, pageable)
                : kycRepository.findByPartyIdAndStatus(partyId, status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<KycProcessResponse> findByStatus(String status, Pageable pageable) {
        return kycRepository.findByStatus(status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public KycProcessResponse findById(UUID id) {
        KycProcess process = kycRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("KYC process not found"));
        return toResponse(process);
    }

    private KycProcessResponse toResponse(KycProcess p) {
        return KycProcessResponse.builder()
                .id(p.getId())
                .partyId(p.getParty().getId())
                .partyName(p.getParty().getName())
                .type(p.getType())
                .status(p.getStatus())
                .riskLevel(p.getRiskLevel())
                .startedAt(p.getStartedAt())
                .completedAt(p.getCompletedAt())
                .expiresAt(p.getExpiresAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
