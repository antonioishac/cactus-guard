package br.com.cactus.guard.alert.service;

import br.com.cactus.guard.alert.dto.AlertCommentResponse;
import br.com.cactus.guard.alert.dto.AlertDetailResponse;
import br.com.cactus.guard.alert.dto.AlertEvidenceItem;
import br.com.cactus.guard.alert.dto.AlertHistoryEntry;
import br.com.cactus.guard.alert.dto.AlertResponse;
import br.com.cactus.guard.alert.dto.PartySummary;
import br.com.cactus.guard.alert.dto.RuleSummary;
import br.com.cactus.guard.alert.dto.TransactionSummary;
import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.entity.AlertHistory;
import br.com.cactus.guard.alert.repository.AlertHistoryRepository;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.alert.repository.AlertSpecifications;
import br.com.cactus.guard.alert.repository.AlertTransactionRepository;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.audit.service.DataAccessService;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.common.audit.AuditPayload;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.security.CurrentUserService;
import br.com.cactus.guard.common.util.DataMasker;
import br.com.cactus.guard.common.validation.Justifications;
import br.com.cactus.guard.notification.service.NotificationService;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertService {

    /** Alertas ainda em tratamento (podem ser escalados / virar case). */
    static final String COMMENT_ACTION = "COMMENT";
    private static final Set<String> ACTIVE = Set.of("OPEN", "IN_ANALYSIS", "ESCALATED");

    private final AlertRepository alertRepository;
    private final AlertHistoryRepository alertHistoryRepository;
    private final AlertTransactionRepository alertTransactionRepository;
    private final MonitoringRuleRepository ruleRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final DataAccessService dataAccessService;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public Page<AlertResponse> findAll(String status, String severity, Pageable pageable) {
        return findAll(new AlertSpecifications.Filter(
                status != null ? List.of(status) : null, severity != null ? List.of(severity) : null,
                null, null, null, null, null, null), pageable);
    }

    /** Listagem filtrada (status/severidade multiplos, tipo, responsavel, periodo, parte, regra). */
    @Transactional(readOnly = true)
    public Page<AlertResponse> findAll(AlertSpecifications.Filter filter, Pageable pageable) {
        return alertRepository.findAll(AlertSpecifications.from(filter), pageable).map(this::toResponse);
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

    /**
     * Detalhe completo: evidencias, transacoes vinculadas, historico, regra/versao e resumo da parte.
     * Nao e readOnly: o acesso a dados da parte (mesmo mascarados) e registrado (LGPD).
     */
    @Transactional
    public AlertDetailResponse findDetail(UUID id) {
        Alert a = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));
        Party party = a.getParty();
        if (party != null) {
            dataAccessService.log("ALERT", id, "party.document(masked),party.riskLevel,party.pep", "CONSULTA", null);
        }
        List<TransactionSummary> txs = alertTransactionRepository.findWithTransactionByAlertId(id).stream()
                .map(at -> TransactionSummary.from(at.getTransaction())).toList();
        List<AlertHistoryEntry> history = alertHistoryRepository.findByAlertIdOrderByCreatedAtDesc(id).stream()
                .map(h -> new AlertHistoryEntry(h.getId(), h.getAction(), h.getFromStatus(), h.getToStatus(),
                        h.getPerformedBy() != null ? h.getPerformedBy().getId() : null,
                        h.getPerformedBy() != null ? h.getPerformedBy().getName() : null,
                        h.getComment(), h.getCreatedAt()))
                .toList();
        return new AlertDetailResponse(a.getId(),
                party != null ? party.getId() : null,
                party != null ? party.getName() : null,
                party != null ? DataMasker.maskDocument(party.getDocument()) : null,
                a.getType(), a.getSeverity(), a.getStatus(), a.getTitle(), a.getDescription(),
                a.getRuleCode(), a.getRuleVersion(),
                a.getAssignedTo() != null ? a.getAssignedTo().getId() : null,
                a.getDueDate(), a.getOccurredAt(), a.getSelectionDeadline(), a.getTotalAmount(),
                a.getFalsePositiveReason(), a.getCreatedAt(),
                evidenceItems(a.getEvidence()), a.getEvidence(),
                txs.stream().map(TransactionSummary::id).toList(), txs, history,
                ruleSummary(a), party != null ? PartySummary.from(party) : null);
    }

    /** Comentarios do alerta (alert_history com action COMMENT), do mais antigo ao mais recente. */
    @Transactional(readOnly = true)
    public List<AlertCommentResponse> listComments(UUID alertId) {
        if (!alertRepository.existsById(alertId)) {
            throw new IllegalArgumentException("Alert not found: " + alertId);
        }
        return alertHistoryRepository.findByAlertIdAndActionOrderByCreatedAtAsc(alertId, COMMENT_ACTION).stream()
                .map(AlertService::toComment).toList();
    }

    /**
     * Comentario livre no alerta (nao altera status). O texto vai ao historico; a auditoria registra
     * apenas o fato e o tamanho (o texto pode conter dados sensiveis).
     */
    @Transactional
    public AlertCommentResponse addComment(UUID alertId, String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException("Comentario nao pode ser vazio");
        }
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));
        User user = currentUser.user().orElse(null);
        AlertHistory saved = alertHistoryRepository.save(AlertHistory.builder()
                .alert(alert).action(COMMENT_ACTION).fromStatus(alert.getStatus()).toStatus(alert.getStatus())
                .performedBy(user).comment(text.trim()).build());
        auditService.log("ALERT_COMMENT_ADDED", "ALERT", alertId, null,
                Map.of("historyId", saved.getId().toString(), "length", saved.getComment().length()));
        return toComment(saved);
    }

    private static AlertCommentResponse toComment(AlertHistory h) {
        return new AlertCommentResponse(h.getId(),
                h.getPerformedBy() != null ? h.getPerformedBy().getId() : null,
                h.getPerformedBy() != null ? h.getPerformedBy().getName() : null,
                h.getComment(), h.getCreatedAt());
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

    /**
     * Escalonamento do alerta para nivel superior: nao e decisao nem encerramento. Justificativa
     * obrigatoria; registra historico e auditoria.
     */
    @Transactional
    public AlertResponse escalate(UUID alertId, String justification) {
        String just = Justifications.require(justification, "escalonamento de alerta");
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        if (!ACTIVE.contains(alert.getStatus())) {
            throw new BusinessException("Alerta encerrado nao pode ser escalado (status " + alert.getStatus() + ")");
        }
        if ("ESCALATED".equals(alert.getStatus())) {
            throw new BusinessException("Alerta ja esta escalado");
        }
        User user = currentUser.user().orElse(null);
        String from = alert.getStatus();
        alert.setStatus("ESCALATED");
        alert = alertRepository.save(alert);
        saveHistory(alert, "ESCALATE", from, "ESCALATED", user, just);
        AuditPayload.log(auditService, "ALERT_ESCALATED", "ALERT", alert.getId(),
                Map.of("status", from), Map.of("status", "ESCALATED", "justification", just));
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
        AuditPayload.log(auditService, "ALERT_FALSE_POSITIVE", "ALERT", alert.getId(),
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

    private RuleSummary ruleSummary(Alert a) {
        MonitoringRule rule = a.getRule();
        if (rule == null && a.getRuleCode() != null) {
            rule = ruleRepository.findByCode(a.getRuleCode()).orElse(null);
        }
        if (rule == null) {
            return a.getRuleCode() != null
                    ? new RuleSummary(a.getRuleCode(), null, a.getRuleVersion(), null, null, null) : null;
        }
        return new RuleSummary(rule.getCode(), rule.getName(),
                a.getRuleVersion() != null ? a.getRuleVersion() : rule.getVersion(),
                rule.getCategory(), rule.getSeverity(), rule.getCarta4001Reference());
    }

    /**
     * Converte o snapshot do disparo em linhas parametro x observado. Valores mapa com chaves
     * {@code threshold}/{@code observed} (e {@code label}) sao usados diretamente; os demais viram observado.
     */
    @SuppressWarnings("unchecked")
    public static List<AlertEvidenceItem> evidenceItems(Map<String, Object> evidence) {
        List<AlertEvidenceItem> items = new ArrayList<>();
        if (evidence == null) {
            return items;
        }
        evidence.forEach((k, v) -> {
            if (v instanceof Map<?, ?> m && (m.containsKey("observed") || m.containsKey("threshold"))) {
                Map<String, Object> mm = (Map<String, Object>) m;
                items.add(new AlertEvidenceItem(k,
                        mm.get("label") != null ? String.valueOf(mm.get("label")) : null,
                        mm.get("threshold"), mm.get("observed")));
            } else {
                items.add(new AlertEvidenceItem(k, null, null,
                        v instanceof Collection || v instanceof Map ? String.valueOf(v) : v));
            }
        });
        return items;
    }

    private AlertResponse toResponse(Alert alert) {
        Party p = alert.getParty();
        return AlertResponse.builder()
                .id(alert.getId())
                .partyId(p != null ? p.getId() : null)
                .partyName(p != null ? p.getName() : null)
                .partyDocument(p != null ? DataMasker.maskDocument(p.getDocument()) : null)
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
