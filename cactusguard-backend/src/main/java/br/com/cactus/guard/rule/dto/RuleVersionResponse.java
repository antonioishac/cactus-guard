package br.com.cactus.guard.rule.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RuleVersionResponse(
        int version,
        Map<String, Object> snapshot,
        String changeReason,
        UUID approvedById,
        Instant approvedAt,
        Instant effectiveFrom,
        Instant effectiveTo,
        UUID createdById,
        String createdByName,
        String approvedByName) {
}
