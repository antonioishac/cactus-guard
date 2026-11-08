package br.com.cactus.guard.rule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
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
    private BigDecimal thresholdValue;
    private Integer thresholdPeriodDays;
}
