package br.com.cactus.guard.alert.entity;

import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert extends br.com.cactus.guard.tenant.TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id")
    private Party party;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "rule_code", length = 50)
    private String ruleCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "closed_at")
    private Instant closedAt;

    // ===================== Campos PLD (V25) =====================

    /** Data da operacao/situacao - inicia a contagem dos 45 dias para selecao. */
    @Column(name = "occurred_at")
    private Instant occurredAt;

    /** occurred_at + 45 dias (Circular 3.978). */
    @Column(name = "selection_deadline")
    private Instant selectionDeadline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private MonitoringRule rule;

    /** Versao da regra vigente no disparo (ver monitoring_rule_versions). */
    @Column(name = "rule_version")
    private Integer ruleVersion;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    /** Chave de deduplicacao: unica entre alertas OPEN/IN_ANALYSIS/ESCALATED. */
    @Column(name = "dedup_key")
    private String dedupKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    /** FALSE_POSITIVE, CASE_OPENED, DUPLICATE, ... */
    @Column(name = "closure_reason", length = 50)
    private String closureReason;

    @Column(name = "closure_justification", columnDefinition = "TEXT")
    private String closureJustification;

    @Column(name = "false_positive_reason", length = 100)
    private String falsePositiveReason;

    /** Snapshot dos parametros/metricas que dispararam o alerta. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence")
    private Map<String, Object> evidence;

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
