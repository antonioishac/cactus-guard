package br.com.cactus.guard.alert.service;

import br.com.cactus.guard.alert.dto.AlertResponse;
import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.entity.AlertHistory;
import br.com.cactus.guard.alert.repository.AlertHistoryRepository;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.notification.service.NotificationService;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.security.CurrentUserService;
import br.com.cactus.guard.common.validation.Justifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertHistoryRepository alertHistoryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public Page<AlertResponse> findAll(String status, String severity, Pageable pageable) {
        Page<Alert> page;
        if (status != null) {
            page = alertRepository.findByStatus(status, pageable);
        } else if (severity != null) {
            page = alertRepository.findBySeverity(severity, pageable);
        } else {
            page = alertRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AlertResponse> findBySeverity(String severity, Pageable pageable) {
        return alertRepository.findBySeverity(severity, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AlertResponse findById(UUID id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));
        return toResponse(alert);
    }

    @Transactional
    public AlertResponse assume(UUID alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String fromStatus = alert.getStatus();
        alert.setStatus("IN_ANALYSIS");
        alert.setAssignedTo(user);
        alert = alertRepository.save(alert);

        notificationService.create(
                user.getId(),
                "ALERT_ASSUMED",
                "Alerta assumido",
                "Você assumiu o alerta: " + alert.getTitle(),
                "ALERT",
                alert.getId()
        );

        saveHistory(alert, "ASSUME", fromStatus, "IN_ANALYSIS", user, null);
        return toResponse(alert);
    }

    @Transactional
    public AlertResponse markFalsePositive(UUID alertId, String justification) {
        return markFalsePositive(alertId, "OTHER", justification);
    }

    /**
     * Encerra o alerta como falso positivo. Justificativa obrigatoria; grava a taxonomia
     * (falsePositiveReason), quem encerrou e quando (alimenta a calibracao das regras).
     */
    @Transactional
    public AlertResponse markFalsePositive(UUID alertId, String reason, String justification) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Motivo do falso positivo e obrigatorio");
        }
        String just = Justifications.require(justification, "marcar alerta como falso positivo");
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        if ("FALSE_POSITIVE".equals(alert.getStatus()) || "CLOSED".equals(alert.getStatus())) {
            throw new BusinessException("Alerta ja encerrado");
        }

        User user = currentUser.user().orElse(null);
        String fromStatus = alert.getStatus();
        Instant now = Instant.now();
        alert.setStatus("FALSE_POSITIVE");
        alert.setClosedAt(now);
        alert.setClosedBy(user);
        alert.setClosureReason("FALSE_POSITIVE");
        alert.setFalsePositiveReason(reason.trim());
        alert.setClosureJustification(just);
        alert = alertRepository.save(alert);

        saveHistory(alert, "FALSE_POSITIVE", fromStatus, "FALSE_POSITIVE", user, just);
        auditService.log("ALERT_FALSE_POSITIVE", "ALERT", alert.getId(),
                Map.of("status", fromStatus),
                Map.of("status", "FALSE_POSITIVE", "falsePositiveReason", reason.trim(), "justification", just));
        return toResponse(alert);
    }

    private void saveHistory(Alert alert, String action, String from, String to, User user, String comment) {
        AlertHistory history = AlertHistory.builder()
                .alert(alert)
                .action(action)
                .fromStatus(from)
                .toStatus(to)
                .performedBy(user)
                .comment(comment)
                .build();
        alertHistoryRepository.save(history);
    }

    private AlertResponse toResponse(Alert alert) {
        return AlertResponse.builder()
                .id(alert.getId())
                .partyId(alert.getParty() != null ? alert.getParty().getId() : null)
                .partyName(alert.getParty() != null ? alert.getParty().getName() : null)
                .type(alert.getType())
                .severity(alert.getSeverity())
                .status(alert.getStatus())
                .title(alert.getTitle())
                .description(alert.getDescription())
                .ruleCode(alert.getRuleCode())
                .assignedToId(alert.getAssignedTo() != null ? alert.getAssignedTo().getId() : null)
                .dueDate(alert.getDueDate())
                .occurredAt(alert.getOccurredAt())
                .selectionDeadline(alert.getSelectionDeadline())
                .ruleVersion(alert.getRuleVersion())
                .totalAmount(alert.getTotalAmount())
                .falsePositiveReason(alert.getFalsePositiveReason())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
