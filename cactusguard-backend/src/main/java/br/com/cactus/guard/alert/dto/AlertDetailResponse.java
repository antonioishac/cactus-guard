package br.com.cactus.guard.alert.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detalhe do alerta: campos de {@link AlertResponse} + evidencias (parametro x observado),
 * transacoes vinculadas, historico, regra/versao e resumo (mascarado) da parte.
 */
public record AlertDetailResponse(
        UUID id,
        UUID partyId,
        String partyName,
        String partyDocument,
        String type,
        String severity,
        String status,
        String title,
        String description,
        String ruleCode,
        Integer ruleVersion,
        UUID assignedToId,
        Instant dueDate,
        Instant occurredAt,
        Instant selectionDeadline,
        BigDecimal totalAmount,
        String falsePositiveReason,
        Instant createdAt,
        List<AlertEvidenceItem> evidence,
        Map<String, Object> evidenceSnapshot,
        List<UUID> triggeringTransactionIds,
        List<TransactionSummary> transactions,
        List<AlertHistoryEntry> history,
        RuleSummary rule,
        PartySummary party) {
}
