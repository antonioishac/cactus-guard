package br.com.cactus.guard.case_.dto;

import br.com.cactus.guard.alert.dto.AlertEvidenceItem;
import br.com.cactus.guard.alert.dto.PartySummary;
import br.com.cactus.guard.alert.dto.TransactionSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dossie consolidado do case (specs 012/033). Toda PII mascarada; o acesso e registrado em
 * data_access_log (LGPD). Nunca exposto em canal do cliente (sem tipping-off).
 */
public record CaseDossierResponse(
        CaseResponse caseInfo,
        AlertInfo alert,
        PartySummary party,
        List<RiskHistoryItem> riskHistory,
        PepInfo pep,
        List<UboItem> ubos,
        List<TransactionSummary> alertTransactions,
        List<TransactionSummary> periodTransactions,
        PeriodWindow period,
        List<ScreeningHitItem> screeningHits,
        List<CaseEvidenceResponse> evidences,
        List<CaseNoteResponse> notes,
        List<AuditItem> auditTrail) {

    public record AlertInfo(UUID id, String type, String severity, String status, String title, String ruleCode,
                            Integer ruleVersion, List<AlertEvidenceItem> evidence) {
    }

    public record PeriodWindow(Instant from, Instant to) {
    }

    public record RiskHistoryItem(String previousLevel, String newLevel, BigDecimal previousScore,
                                  BigDecimal newScore, String reason, String changedByName, Instant createdAt) {
    }

    public record PepInfo(boolean pep, String status, String category, String role, LocalDate startDate,
                          LocalDate endDate, LocalDate expiryDate, UUID approvedById, String approvedByName,
                          Instant approvedAt) {
    }

    public record UboItem(UUID id, String name, String document, BigDecimal ownershipPct, String controlType,
                          String ownerType, boolean pep, boolean finalBeneficiary, boolean indirect,
                          List<Map<String, Object>> ownershipChain) {
    }

    public record ScreeningHitItem(UUID id, String listCode, String listType, String matchedName,
                                   BigDecimal matchScore, String matchType, String status,
                                   String reviewJustification, Instant reviewedAt, Instant createdAt) {
    }

    public record AuditItem(String action, UUID userId, String userName, Map<String, Object> details,
                            Instant createdAt) {
    }
}
