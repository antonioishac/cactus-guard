package br.com.cactus.guard.rule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// construtor vazio: necessario para desserializar o valor cacheado no Redis
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringRuleResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private String category;
    private String severity;
    private boolean active;
    private String status;
    private int version;
    private Map<String, Object> parameters;
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private BigDecimal thresholdValue;
    private Integer thresholdPeriodDays;

    // ---- governanca (V27) ----
    /** Quem criou / ultima alteracao pendente (base do quatro olhos). */
    private UUID createdById;
    private String createdByName;
    private UUID approvedById;
    private String approvedByName;
    private Instant approvedAt;
    private String carta4001Reference;
    private List<String> appliesToRiskLevels;
    private boolean autoCommunication;
    /** Versao imutavel vigente agora (somente em GET /rules/{code}); null se a regra nao esta em vigor. */
    private Integer activeVersion;

    // ---- metricas (janela padrao: ultimos 30 dias; ver GET /rules/{code}/metrics) ----
    private Long alertsGenerated;
    /** falsos positivos / alertas encerrados (FALSE_POSITIVE + CLOSED); null se nenhum encerrado no periodo. */
    private Double falsePositiveRate;
}
