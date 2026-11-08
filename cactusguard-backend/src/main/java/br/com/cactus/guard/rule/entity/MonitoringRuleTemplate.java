package br.com.cactus.guard.rule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regra-modelo do catalogo GLOBAL da plataforma (V37): copiada para o tenant no provisionamento.
 * Sem tenant_id e sem RLS.
 */
@Entity
@Table(name = "monitoring_rule_templates")
@Getter
@Setter
@NoArgsConstructor
public class MonitoringRuleTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "condition_expression", columnDefinition = "TEXT")
    private String conditionExpression;

    @Column(name = "threshold_value", precision = 18, scale = 2)
    private BigDecimal thresholdValue;

    @Column(name = "threshold_period_days")
    private Integer thresholdPeriodDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters")
    private Map<String, Object> parameters;

    @Column(name = "carta_4001_reference")
    private String carta4001Reference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applies_to_risk_levels")
    private List<String> appliesToRiskLevels;

    @Column(name = "auto_communication", nullable = false)
    private boolean autoCommunication;
}
