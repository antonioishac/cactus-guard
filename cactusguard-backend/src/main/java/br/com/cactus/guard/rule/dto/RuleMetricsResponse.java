package br.com.cactus.guard.rule.dto;

import java.time.Instant;

/** Metricas de calibracao da regra no periodo (spec 013). */
public record RuleMetricsResponse(
        String code,
        Instant from,
        Instant to,
        long alertsGenerated,
        long alertsClosed,
        long falsePositives,
        /** falsePositives / alertsClosed; null quando nao ha alertas encerrados. */
        Double falsePositiveRate) {
}
