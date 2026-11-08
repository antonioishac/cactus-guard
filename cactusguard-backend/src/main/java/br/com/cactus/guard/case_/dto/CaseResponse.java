package br.com.cactus.guard.case_.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CaseResponse {
    private UUID id;
    /** Identificador legivel sequencial por ano (ex.: CG-2026-000123). */
    private String caseNumber;
    private UUID alertId;
    private UUID partyId;
    private String partyName;
    /** Nivel de risco atual da parte (LOW, MEDIUM, HIGH, CRITICAL). */
    private String partyRiskLevel;
    private String status;
    private String priority;
    private UUID assignedToId;
    private String decision;
    private String decisionJustification;
    private UUID decidedById;
    private Instant decidedAt;
    private Instant slaDueDate;
    /** ON_TIME, DUE_SOON ou OVERDUE pelo prazo regulatorio vigente; null se encerrado. */
    private String slaStatus;
    private Instant occurredAt;
    /** Prazo de selecao do alerta de origem (ocorrencia + 45 dias). */
    private Instant selectionDeadline;
    private Instant selectedAt;
    private Instant analysisDeadline;
    private Instant communicationDeadline;
    private int escalationLevel;
    private boolean edd;
    private List<String> indicatorCodes;
    private BigDecimal totalAmountInvolved;
    private UUID approvedById;
    private Instant approvedAt;
    private Instant closedAt;
    private Instant createdAt;
}
