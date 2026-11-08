package br.com.cactus.guard.rule.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Criacao/edicao de regra. {@code reason} e obrigatoria na edicao. */
public record MonitoringRuleRequest(
        String code,
        @NotBlank String name,
        String description,
        @NotBlank String category,
        @NotBlank String severity,
        BigDecimal thresholdValue,
        Integer thresholdPeriodDays,
        Map<String, Object> parameters,
        List<String> appliesToRiskLevels,
        String carta4001Reference,
        String reason) {
}
