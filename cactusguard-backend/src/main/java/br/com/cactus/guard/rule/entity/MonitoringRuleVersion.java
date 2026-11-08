package br.com.cactus.guard.rule.entity;

import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshot imutavel de uma versao de regra (V27). Permite reconstruir qual parametro
 * estava vigente quando um alerta foi (ou deixou de ser) gerado.
 * O banco rejeita DELETE e qualquer UPDATE que nao seja o encerramento de vigencia
 * (effective_to de NULL para um valor).
 */
@Entity
@Table(name = "monitoring_rule_versions",
       uniqueConstraints = @UniqueConstraint(name = "uq_rule_versions", columnNames = {"rule_id", "version"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoringRuleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false, updatable = false)
    private MonitoringRule rule;

    @Column(nullable = false, updatable = false)
    private int version;

    /** Estado completo da regra nesta versao. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, updatable = false)
    private Map<String, Object> snapshot;

    @Column(name = "change_reason", columnDefinition = "TEXT", updatable = false)
    private String changeReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", updatable = false)
    private User approvedBy;

    @Column(name = "approved_at", updatable = false)
    private Instant approvedAt;

    @Column(name = "effective_from", updatable = false)
    private Instant effectiveFrom;

    /** Unico campo atualizavel (uma vez), ao encerrar a vigencia. */
    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
