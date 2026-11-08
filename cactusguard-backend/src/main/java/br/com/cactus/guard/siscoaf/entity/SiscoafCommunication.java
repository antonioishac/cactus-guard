package br.com.cactus.guard.siscoaf.entity;

import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.transaction.entity.Transaction;
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
@Table(name = "siscoaf_communications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiscoafCommunication extends br.com.cactus.guard.tenant.TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private Case caseEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id")
    private Party party;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "protocol_number", length = 50)
    private String protocolNumber;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    // ===================== Campos PLD (V26) =====================
    // type: COS, COE (especie >= R$ 50 mil, automatica), NEGATIVE_DECLARATION,
    //       COAF_COMMUNICATION / SAR (legado).

    /** Dia util seguinte a decided_at. */
    @Column(name = "deadline_at")
    private Instant deadlineAt;

    /** Momento da decisao de comunicar (inicia o prazo). */
    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Ano de referencia da declaracao negativa (obrigatorio para NEGATIVE_DECLARATION). */
    @Column(name = "reference_year")
    private Integer referenceYear;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "indicator_codes")
    private List<String> indicatorCodes;

    /** Comunicacao retificada por esta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_communication_id")
    private SiscoafCommunication originalCommunication;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    /** Operacao em especie comunicada (COE) - no maximo uma COE vigente por operacao. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

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
