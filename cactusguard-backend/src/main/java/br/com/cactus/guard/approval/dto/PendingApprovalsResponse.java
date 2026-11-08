package br.com.cactus.guard.approval.dto;

import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Fila de aprovacao do SUPERVISOR: cases, regras e PEPs aguardando segunda aprovacao (quatro olhos). */
public record PendingApprovalsResponse(
        Page<CaseItem> cases,
        Page<RuleItem> rules,
        Page<PepItem> peps,
        Counts counts) {

    public record Counts(long cases, long rules, long peps) {
    }

    /** {@code ownDecision}: o usuario atual decidiu o case e, portanto, nao pode aprovar. */
    public record CaseItem(UUID id, String caseNumber, UUID partyId, String partyName, String partyRiskLevel,
                           String decision, UUID decidedById, Instant decidedAt, boolean edd,
                           String slaStatus, Instant analysisDeadline, boolean ownDecision) {
    }

    /** {@code ownChange}: o usuario atual criou/alterou a regra e, portanto, nao pode aprova-la. */
    public record RuleItem(String code, String name, String category, String severity, int version,
                           UUID createdById, String createdByName, Instant updatedAt, boolean ownChange) {
    }

    /** Documento sempre mascarado. */
    public record PepItem(UUID partyId, String partyName, String document, String pepCategory, String pepRole,
                          LocalDate pepStartDate, String riskLevel, Instant markedAt) {
    }
}
