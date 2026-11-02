package br.com.cactus.guard.rule.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
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
