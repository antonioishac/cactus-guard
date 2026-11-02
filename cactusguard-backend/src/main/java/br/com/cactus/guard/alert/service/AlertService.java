package br.com.cactus.guard.alert.service;

import br.com.cactus.guard.alert.dto.AlertResponse;
import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.entity.AlertHistory;
import br.com.cactus.guard.alert.repository.AlertHistoryRepository;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
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
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertHistoryRepository alertHistoryRepository;
    private final UserRepository userRepository;

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
    public AlertResponse markFalsePositive(UUID alertId, String comment) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElse(null);

        String fromStatus = alert.getStatus();
        alert.setStatus("FALSE_POSITIVE");
        alert.setClosedAt(Instant.now());
        alert = alertRepository.save(alert);

        saveHistory(alert, "FALSE_POSITIVE", fromStatus, "FALSE_POSITIVE", user, comment);
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
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
