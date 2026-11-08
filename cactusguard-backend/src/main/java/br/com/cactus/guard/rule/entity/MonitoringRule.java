package br.com.cactus.guard.rule.entity;

import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "monitoring_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoringRule {

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
    @Builder.Default
    private boolean active = true;

    @Column(name = "condition_expression", columnDefinition = "TEXT")
    private String conditionExpression;

    @Column(name = "threshold_value", precision = 18, scale = 2)
    private BigDecimal thresholdValue;

    @Column(name = "threshold_period_days")
    private Integer thresholdPeriodDays;

    // ===================== Governanca / versionamento (V27) =====================

    @Column(nullable = false)
    @Builder.Default
    private int version = 1;

    /** Parametros do cenario (limiares, janelas, multiplicadores). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters")
    private Map<String, Object> parameters;

    /** DRAFT, PENDING_APPROVAL, ACTIVE, INACTIVE. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "carta_4001_reference")
    private String carta4001Reference;

    /** Niveis de risco a que a regra se aplica (ex.: ["HIGH","CRITICAL"]); null = todos. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applies_to_risk_levels")
    private List<String> appliesToRiskLevels;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    /** Disparo gera comunicacao automatica ao COAF (ex.: CASH_50K -> COE). */
    @Column(name = "auto_communication", nullable = false)
    @Builder.Default
    private boolean autoCommunication = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
