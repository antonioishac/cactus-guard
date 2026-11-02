package br.com.cactus.guard.case_.service;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.dto.CaseResponse;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.audit.service.DataAccessService;
import br.com.cactus.guard.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaseService {

    private final CaseRepository caseRepository;
    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional
    public CaseResponse createFromAlert(UUID alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        Case caseEntity = Case.builder()
                .alert(alert)
                .party(alert.getParty())
                .status("OPEN")
                .priority(mapSeverityToPriority(alert.getSeverity()))
                .slaDueDate(calculateSla(alert.getSeverity()))
                .build();

        caseEntity = caseRepository.save(caseEntity);
        return toResponse(caseEntity);
    }

    @Transactional(readOnly = true)
    public Page<CaseResponse> findAll(String status, Pageable pageable) {
        Page<Case> page = status != null
                ? caseRepository.findByStatus(status, pageable)
                : caseRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CaseResponse findById(UUID id) {
        Case c = caseRepository.findById(id)
                .orElseThrow(() -> new br.com.cactus.guard.common.exception.BusinessException("Case not found"));
        dataAccessService.log("CASE", id, "decision,justification", "CONSULTA", null);
        return toResponse(c);
    }

    @Transactional
    public CaseResponse decide(UUID caseId, CaseDecisionRequest request) {
        if (request.getJustification() == null || request.getJustification().isBlank()) {
            throw new br.com.cactus.guard.common.exception.BusinessException("Justificativa é obrigatória para decisão de case");
        }

        Case c = caseRepository.findById(caseId)
                .orElseThrow(() -> new br.com.cactus.guard.common.exception.BusinessException("Case não encontrado"));

        if (c.getDecidedAt() != null) {
            throw new br.com.cactus.guard.common.exception.BusinessException("Case já foi decidido. Não é possível alterar decisão final.");
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElse(null);

        c.setDecision(request.getDecision());
        c.setDecisionJustification(request.getJustification());
        c.setDecidedBy(user);
        c.setDecidedAt(Instant.now());
        c.setStatus(mapDecisionToStatus(request.getDecision()));

        c = caseRepository.save(c);

        if (user != null) {
            notificationService.create(
                    user.getId(),
                    "CASE_DECIDED",
                    "Case decidido",
                    "Decisão " + request.getDecision() + " registrada no case",
                    "CASE",
                    c.getId()
            );
            auditService.log("DECIDE", "CASE", c.getId(), null, 
                    java.util.Map.of("decision", request.getDecision(), "justification", request.getJustification()));
        }

        return toResponse(c);
    }

    private Instant calculateSla(String severity) {
        return switch (severity) {
            case "CRITICAL" -> Instant.now().plusSeconds(24 * 60 * 60);
            case "HIGH" -> Instant.now().plusSeconds(72 * 60 * 60);
            case "MEDIUM" -> Instant.now().plusSeconds(5 * 24 * 60 * 60);
            default -> Instant.now().plusSeconds(10 * 24 * 60 * 60);
        };
    }

    private String mapSeverityToPriority(String severity) {
        return switch (severity) {
            case "CRITICAL" -> "HIGH";
            case "HIGH" -> "HIGH";
            case "MEDIUM" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private String mapDecisionToStatus(String decision) {
        return switch (decision) {
            case "APPROVED" -> "APPROVED";
            case "REJECTED" -> "REJECTED";
            case "ESCALATED" -> "ESCALATED";
            default -> "CLOSED";
        };
    }

    private CaseResponse toResponse(Case c) {
        return CaseResponse.builder()
                .id(c.getId())
                .alertId(c.getAlert() != null ? c.getAlert().getId() : null)
                .partyId(c.getParty() != null ? c.getParty().getId() : null)
                .partyName(c.getParty() != null ? c.getParty().getName() : null)
                .status(c.getStatus())
                .priority(c.getPriority())
                .assignedToId(c.getAssignedTo() != null ? c.getAssignedTo().getId() : null)
                .decision(c.getDecision())
                .decisionJustification(c.getDecisionJustification())
                .decidedAt(c.getDecidedAt())
                .slaDueDate(c.getSlaDueDate())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
