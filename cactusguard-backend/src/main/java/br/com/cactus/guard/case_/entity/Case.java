package br.com.cactus.guard.case_.entity;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.party.entity.Party;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Case extends br.com.cactus.guard.tenant.TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id")
    private Party party;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(length = 20)
    @Builder.Default
    private String priority = "MEDIUM";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(length = 20)
    private String decision;

    @Column(name = "decision_justification", columnDefinition = "TEXT")
    private String decisionJustification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "sla_due_date")
    private Instant slaDueDate;

    // ===================== Campos PLD (V25) =====================

    /** Identificador legivel do caso (ex.: CG-2026-000123). */
    @Column(name = "case_number", length = 30)
    private String caseNumber;

    /** Data da operacao mais antiga do caso (prova dos 45 dias ate a selecao). */
    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "selected_at")
    private Instant selectedAt;

    /** selected_at + 45 dias. */
    @Column(name = "analysis_deadline")
    private Instant analysisDeadline;

    /** Dia util seguinte a decisao de comunicar. */
    @Column(name = "communication_deadline")
    private Instant communicationDeadline;

    @Column(name = "escalation_level", nullable = false)
    @Builder.Default
    private int escalationLevel = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalated_to")
    private User escalatedTo;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "is_edd", nullable = false)
    @Builder.Default
    private boolean edd = false;

    /** Segunda aprovacao (quatro olhos); o banco impede approved_by = decided_by. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Codigos de indicios da Carta Circular 4.001/2020. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "indicator_codes")
    private List<String> indicatorCodes;

    @Column(name = "total_amount_involved", precision = 18, scale = 2)
    private BigDecimal totalAmountInvolved;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "dossier_summary", columnDefinition = "TEXT")
    private String dossierSummary;

    @Column(name = "closed_at")
    private Instant closedAt;

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
