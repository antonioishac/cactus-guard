package br.com.cactus.guard.rule.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
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
    private boolean active = true;

    @Column(name = "condition_expression", columnDefinition = "TEXT")
    private String conditionExpression;

    @Column(name = "threshold_value", precision = 18, scale = 2)
    private BigDecimal thresholdValue;

    @Column(name = "threshold_period_days")
    private Integer thresholdPeriodDays;

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
