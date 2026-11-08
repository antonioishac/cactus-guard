package br.com.cactus.guard.case_.service;

import br.com.cactus.guard.event.dto.DomainEventType;
import br.com.cactus.guard.event.service.DomainEventPublisher;
import br.com.cactus.guard.event.service.EventPayloads;
import br.com.cactus.guard.common.audit.AuditPayload;
import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.audit.service.DataAccessService;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.case_.dto.CaseApprovalRequest;
import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.dto.CaseEscalationRequest;
import br.com.cactus.guard.case_.dto.CaseResponse;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.common.event.CasePendingApprovalEvent;
import org.springframework.context.ApplicationEventPublisher;
import br.com.cactus.guard.case_.repository.CaseSpecifications;
import br.com.cactus.guard.alert.entity.AlertHistory;
import br.com.cactus.guard.alert.repository.AlertHistoryRepository;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.common.calendar.RegulatoryDeadlines;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.security.CurrentUserService;
import br.com.cactus.guard.common.validation.Justifications;
import br.com.cactus.guard.notification.service.NotificationService;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import br.com.cactus.guard.siscoaf.service.SiscoafDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ciclo de vida do case (Circular BCB 3.978/2020):
 * selecao (45d da ocorrencia) -> analise (45d da selecao) -> decisao humana COMMUNICATE | ARCHIVE
 * (justificativa obrigatoria) -> aprovacao por segundo usuario (quatro olhos) quando exigida ->
 * comunicacao ao COAF ate o dia util seguinte a aprovacao. Case com decisao final e imutavel.
 */
@Service
@RequiredArgsConstructor
public class CaseService {

    public static final Set<String> FINAL_STATUSES = Set.of("COMMUNICATE", "COMMUNICATED", "ARCHIVED");
    private static final Set<String> VALID_DECISIONS = Set.of("COMMUNICATE", "ARCHIVE");

    private final CaseRepository caseRepository;
    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final DataAccessService dataAccessService;
    private final CurrentUserService currentUser;
    private final RegulatoryDeadlines deadlines;
    private final SiscoafDraftService draftService;
    private final CaseNumberService caseNumberService;
    private final ApplicationEventPublisher eventPublisher;
    private final AlertHistoryRepository alertHistoryRepository;
    private final DomainEventPublisher events;

    /** Selecao sem justificativa textual (alias legado POST /cases/from-alert/{id}). */
    @Transactional
    public CaseResponse createFromAlert(UUID alertId) {
        return createFromAlert(alertId, null);
    }

    /**
     * Seleciona o alerta e abre o case. A justificativa (quando informada) e registrada na auditoria
     * e no historico do alerta; em POST /cases ela e obrigatoria (validada no controller/DTO).
     */
    @Transactional
    public CaseResponse createFromAlert(UUID alertId, String justification) {
        String selectionJustification = justification == null ? null
                : Justifications.require(justification, "selecao do alerta (abertura de case)");
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        Instant selectedAt = Instant.now();
        Instant analysisDeadline = deadlines.analysisDeadline(selectedAt);
        Party party = alert.getParty();
        String severity = alert.getSeverity() != null ? alert.getSeverity() : "MEDIUM";

        Case caseEntity = Case.builder()
                .alert(alert)
                .party(party)
                .status("OPEN")
                .priority(mapSeverityToPriority(severity))
                .slaDueDate(RegulatoryDeadlines.min(calculateSla(severity, selectedAt), analysisDeadline))
                .selectedAt(selectedAt)
                .analysisDeadline(analysisDeadline)
                .occurredAt(alert.getOccurredAt())
                .edd(party != null && party.isEddRequired())
                .totalAmountInvolved(alert.getTotalAmount())
                .indicatorCodes(alert.getRuleCode() != null ? List.of(alert.getRuleCode()) : null)
                .caseNumber(caseNumberService.next())
                .build();

        caseEntity = caseRepository.save(caseEntity);

        User selector = currentUser.user().orElse(null);
        alertHistoryRepository.save(AlertHistory.builder()
                .alert(alert).action("CASE_OPENED").fromStatus(alert.getStatus()).toStatus(alert.getStatus())
                .performedBy(selector).comment(selectionJustification).build());

        Map<String, Object> created = new LinkedHashMap<>();
        created.put("caseNumber", caseEntity.getCaseNumber());
        if (selectionJustification != null) {
            created.put("selectionJustification", selectionJustification);
        }
        created.put("alertId", alertId.toString());
        created.put("selectedAt", selectedAt.toString());
        created.put("analysisDeadline", analysisDeadline.toString());
        created.put("slaDueDate", caseEntity.getSlaDueDate().toString());
        AuditPayload.log(auditService, "CASE_CREATED", "CASE", caseEntity.getId(), null, created);

        Instant selectionDeadline = alert.getSelectionDeadline();
        if (selectionDeadline != null && selectedAt.isAfter(selectionDeadline)) {
            // Selecao intempestiva: registra, mas nao bloqueia (o case precisa existir para ser tratado)
            Map<String, Object> late = new LinkedHashMap<>();
            late.put("alertId", alertId.toString());
            late.put("selectionDeadline", selectionDeadline.toString());
            late.put("selectedAt", selectedAt.toString());
            late.put("daysLate", Duration.between(selectionDeadline, selectedAt).toDays());
            AuditPayload.log(auditService, "CASE_LATE_SELECTION", "CASE", caseEntity.getId(), null, late);
        }
        return toResponse(caseEntity);
    }

    @Transactional(readOnly = true)
    public Page<CaseResponse> findAll(String status, Pageable pageable) {
        Page<Case> page = status != null
                ? caseRepository.findByStatus(status, pageable)
                : caseRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    /** Listagem filtrada (status multiplos, responsavel, SLA regulatorio, risco da parte, EDD, periodo). */
    @Transactional(readOnly = true)
    public Page<CaseResponse> findAll(CaseSpecifications.Filter filter, Pageable pageable) {
        Instant now = Instant.now();
        return caseRepository.findAll(CaseSpecifications.from(filter, now), pageable)
                .map(c -> toResponse(c, now));
    }

    /**
     * Cases atribuídos a um analista; {@code assigneeId == null} retorna a fila de cases sem responsável.
     */
    @Transactional(readOnly = true)
    public Page<CaseResponse> findByAssignee(UUID assigneeId, Pageable pageable) {
        Page<Case> page = assigneeId != null
                ? caseRepository.findByAssignedToId(assigneeId, pageable)
                : caseRepository.findByAssignedToIsNull(pageable);
        return page.map(this::toResponse);
    }

    /** Quantidade de cases em aberto, isto é, ainda sem decisão final. */
    @Transactional(readOnly = true)
    public long countOpen() {
        return caseRepository.countByDecidedAtIsNull();
    }

    /** Nao e readOnly: o acesso a decisao/justificativa e registrado (LGPD) na mesma transacao. */
    @Transactional
    public CaseResponse findById(UUID id) {
        Case c = caseRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Case not found"));
        dataAccessService.log("CASE", id, "decision,justification", "CONSULTA", null);
        return toResponse(c);
    }

    /**
     * Registra a decisao humana (COMMUNICATE ou ARCHIVE). Decisao COMMUNICATE, party PEP, risco
     * HIGH/CRITICAL ou case EDD exigem segunda aprovacao (PENDING_APPROVAL); os demais ARCHIVE
     * ficam ARCHIVED direto.
     */
    @Transactional
    public CaseResponse decide(UUID caseId, CaseDecisionRequest request) {
        String decision = request.getDecision();
        if (decision == null || !VALID_DECISIONS.contains(decision)) {
            throw new BusinessException("Decisao invalida: '" + decision
                    + "'. Use COMMUNICATE ou ARCHIVE (escalonamento: POST /cases/{id}/escalate)");
        }
        String justification = Justifications.require(request.getJustification(), "decisao de case");

        Case c = caseRepository.findById(caseId)
                .orElseThrow(() -> new BusinessException("Case não encontrado"));
        assertNotFinal(c);

        User user = currentUser.user().orElse(null);
        Map<String, Object> before = snapshot(c);
        Instant now = Instant.now();

        c.setDecision(decision);
        c.setDecisionJustification(justification);
        c.setDecidedBy(user);
        c.setDecidedAt(now);

        if (requiresApproval(c, decision)) {
            c.setStatus("PENDING_APPROVAL");
        } else {
            c.setStatus("ARCHIVED");
            c.setClosedAt(now);
        }
        c = caseRepository.save(c);
        if ("PENDING_APPROVAL".equals(c.getStatus())) {
            eventPublisher.publishEvent(new CasePendingApprovalEvent(c.getId(),
                    c.getDecidedBy() != null ? c.getDecidedBy().getId() : null,
                    br.com.cactus.guard.tenant.TenantContext.require()));
        }

        if (user != null) {
            notificationService.create(user.getId(), "CASE_DECIDED", "Case decidido",
                    "Decisão " + decision + " registrada no case", "CASE", c.getId());
        }
        AuditPayload.log(auditService, "CASE_DECIDED", "CASE", c.getId(), before, snapshot(c));
        events.publish(DomainEventType.CASE_DECIDED, c.getId(), EventPayloads.caseEvent(c));
        return toResponse(c);
    }

    /** Segunda aprovacao (quatro olhos): SUPERVISOR diferente de quem decidiu. */
    @Transactional
    public CaseResponse approve(UUID caseId, CaseApprovalRequest request) {
        if (!currentUser.hasRole("SUPERVISOR")) {
            throw new AccessDeniedException("Aprovacao de case exige perfil SUPERVISOR");
        }
        Case c = caseRepository.findById(caseId)
                .orElseThrow(() -> new BusinessException("Case não encontrado"));
        if (!"PENDING_APPROVAL".equals(c.getStatus())) {
            throw new BusinessException("Case nao esta aguardando aprovacao (status " + c.getStatus() + ")");
        }
        User approver = currentUser.user()
                .orElseThrow(() -> new BusinessException("Aprovador nao identificado"));
        if (c.getDecidedBy() == null) {
            throw new BusinessException("Decisor nao identificado: aprovacao impossivel");
        }
        if (approver.getId().equals(c.getDecidedBy().getId())) {
            throw new BusinessException("Quatro olhos: o aprovador deve ser diferente de quem decidiu o case");
        }

        Map<String, Object> before = snapshot(c);
        Instant now = Instant.now();
        c.setApprovedBy(approver);
        c.setApprovedAt(now);

        SiscoafCommunication cos = null;
        if ("COMMUNICATE".equals(c.getDecision())) {
            c.setStatus("COMMUNICATE");
            c.setCommunicationDeadline(deadlines.communicationDeadline(now));
            c = caseRepository.save(c);
            // Rascunho apenas: o envio ao COAF e acao explicita posterior
            cos = draftService.createCosDraft(c, now, c.getCommunicationDeadline());
        } else {
            c.setStatus("ARCHIVED");
            c.setClosedAt(now);
            c = caseRepository.save(c);
        }

        Map<String, Object> after = snapshot(c);
        if (cos != null) {
            after.put("cosDraftId", cos.getId().toString());
        }
        if (request != null && request.comment() != null && !request.comment().isBlank()) {
            after.put("approvalComment", request.comment().trim());
        }
        AuditPayload.log(auditService, "CASE_APPROVED", "CASE", c.getId(), before, after);
        events.publish(DomainEventType.CASE_APPROVED, c.getId(), EventPayloads.caseEvent(c));
        return toResponse(c);
    }

    /**
     * Rejeicao da aprovacao (quatro olhos): SUPERVISOR diferente de quem decidiu devolve o case para
     * analise. A decisao pendente e limpa (o case volta a aceitar nova decisao), mas a decisao rejeitada
     * e a justificativa da rejeicao permanecem na trilha de auditoria (audit_log append-only).
     */
    @Transactional
    public CaseResponse rejectApproval(UUID caseId, String justification) {
        if (!currentUser.hasRole("SUPERVISOR")) {
            throw new AccessDeniedException("Rejeicao de aprovacao exige perfil SUPERVISOR");
        }
        String just = Justifications.require(justification, "rejeicao de aprovacao de case");
        Case c = caseRepository.findById(caseId)
                .orElseThrow(() -> new BusinessException("Case não encontrado"));
        if (!"PENDING_APPROVAL".equals(c.getStatus())) {
            throw new BusinessException("Case nao esta aguardando aprovacao (status " + c.getStatus() + ")");
        }
        User reviewer = currentUser.user()
                .orElseThrow(() -> new BusinessException("Revisor nao identificado"));
        User decider = c.getDecidedBy();
        if (decider == null) {
            throw new BusinessException("Decisor nao identificado: rejeicao impossivel");
        }
        if (reviewer.getId().equals(decider.getId())) {
            throw new BusinessException("Quatro olhos: a rejeicao deve ser feita por SUPERVISOR diferente de quem decidiu o case");
        }

        Map<String, Object> before = snapshot(c);
        String rejectedDecision = c.getDecision();
        c.setDecision(null);
        c.setDecisionJustification(null);
        c.setDecidedBy(null);
        c.setDecidedAt(null);
        c.setApprovedBy(null);
        c.setApprovedAt(null);
        c.setCommunicationDeadline(null);
        c.setStatus("IN_ANALYSIS");
        c = caseRepository.save(c);

        Map<String, Object> after = snapshot(c);
        after.put("rejectedDecision", rejectedDecision);
        after.put("rejectedDecisionBy", decider.getId().toString());
        after.put("rejectionJustification", just);
        after.put("rejectedBy", reviewer.getId().toString());
        AuditPayload.log(auditService, "CASE_APPROVAL_REJECTED", "CASE", c.getId(), before, after);
        // payload sem a justificativa (texto livre pode conter PII); o decisor/rejeitor ficam no audit_log
        events.publish(DomainEventType.CASE_APPROVAL_REJECTED, c.getId(), EventPayloads.caseEvent(c));

        notificationService.create(decider.getId(), "CASE_APPROVAL_REJECTED", "Aprovação rejeitada",
                "A decisão proposta foi devolvida para análise", "CASE", c.getId());
        return toResponse(c);
    }

    /** Escalonamento: nao e decisao final; exige justificativa e incrementa o nivel. */
    @Transactional
    public CaseResponse escalate(UUID caseId, CaseEscalationRequest request) {
        String justification = Justifications.require(request.justification(), "escalonamento de case");
        Case c = caseRepository.findById(caseId)
                .orElseThrow(() -> new BusinessException("Case não encontrado"));
        assertNotFinal(c);
        if ("PENDING_APPROVAL".equals(c.getStatus())) {
            throw new BusinessException("Case aguardando aprovacao nao pode ser escalado");
        }

        User target = null;
        if (request.escalatedToId() != null) {
            target = userRepository.findById(request.escalatedToId())
                    .orElseThrow(() -> new BusinessException("Usuario de escalonamento nao encontrado"));
        }
        Map<String, Object> before = snapshot(c);
        c.setEscalationLevel(c.getEscalationLevel() + 1);
        c.setEscalatedTo(target);
        c.setEscalatedAt(Instant.now());
        c.setStatus("ESCALATED");
        c = caseRepository.save(c);

        Map<String, Object> after = snapshot(c);
        after.put("escalationJustification", justification);
        AuditPayload.log(auditService, "CASE_ESCALATED", "CASE", c.getId(), before, after);
        return toResponse(c);
    }

    private void assertNotFinal(Case c) {
        if (c.getDecidedAt() != null || FINAL_STATUSES.contains(c.getStatus())
                || "PENDING_APPROVAL".equals(c.getStatus()) && c.getDecision() != null) {
            throw new BusinessException("Case já foi decidido. Não é possível alterar decisão final.");
        }
    }

    private boolean requiresApproval(Case c, String decision) {
        if ("COMMUNICATE".equals(decision) || c.isEdd()) {
            return true;
        }
        Party p = c.getParty();
        if (p == null) {
            return false;
        }
        return p.isPep() || "HIGH".equals(p.getRiskLevel()) || "CRITICAL".equals(p.getRiskLevel());
    }

    private Map<String, Object> snapshot(Case c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", c.getStatus());
        m.put("decision", c.getDecision());
        m.put("escalationLevel", c.getEscalationLevel());
        m.put("decidedBy", c.getDecidedBy() != null ? c.getDecidedBy().getId().toString() : null);
        m.put("approvedBy", c.getApprovedBy() != null ? c.getApprovedBy().getId().toString() : null);
        m.put("communicationDeadline", c.getCommunicationDeadline() != null ? c.getCommunicationDeadline().toString() : null);
        if (c.getDecisionJustification() != null) {
            m.put("justification", c.getDecisionJustification());
        }
        return m;
    }

    private Instant calculateSla(String severity, Instant from) {
        return switch (severity) {
            case "CRITICAL" -> from.plusSeconds(24 * 60 * 60);
            case "HIGH" -> from.plusSeconds(72 * 60 * 60);
            case "MEDIUM" -> from.plusSeconds(5 * 24 * 60 * 60);
            default -> from.plusSeconds(10 * 24 * 60 * 60);
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

    public CaseResponse toResponse(Case c) {
        return toResponse(c, Instant.now());
    }

    private CaseResponse toResponse(Case c, Instant now) {
        return CaseResponse.builder()
                .id(c.getId())
                .caseNumber(c.getCaseNumber())
                .alertId(c.getAlert() != null ? c.getAlert().getId() : null)
                .partyId(c.getParty() != null ? c.getParty().getId() : null)
                .partyName(c.getParty() != null ? c.getParty().getName() : null)
                .partyRiskLevel(c.getParty() != null ? c.getParty().getRiskLevel() : null)
                .status(c.getStatus())
                .priority(c.getPriority())
                .assignedToId(c.getAssignedTo() != null ? c.getAssignedTo().getId() : null)
                .decision(c.getDecision())
                .decisionJustification(c.getDecisionJustification())
                .decidedById(c.getDecidedBy() != null ? c.getDecidedBy().getId() : null)
                .decidedAt(c.getDecidedAt())
                .slaDueDate(c.getSlaDueDate())
                .slaStatus(SlaCalculator.classify(c, now))
                .occurredAt(c.getOccurredAt())
                .selectionDeadline(c.getAlert() != null ? c.getAlert().getSelectionDeadline() : null)
                .selectedAt(c.getSelectedAt())
                .analysisDeadline(c.getAnalysisDeadline())
                .communicationDeadline(c.getCommunicationDeadline())
                .escalationLevel(c.getEscalationLevel())
                .edd(c.isEdd())
                .indicatorCodes(c.getIndicatorCodes())
                .totalAmountInvolved(c.getTotalAmountInvolved())
                .approvedById(c.getApprovedBy() != null ? c.getApprovedBy().getId() : null)
                .approvedAt(c.getApprovedAt())
                .closedAt(c.getClosedAt())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
