package br.com.cactus.guard.screening.entity;

import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.party.entity.Party;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "screening_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreeningResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id")
    private ScreeningEntry entry;

    @Column(name = "list_code", length = 50)
    private String listCode;

    @Column(name = "match_score", precision = 5, scale = 2)
    private BigDecimal matchScore;

    @Column(name = "match_type", length = 30)
    private String matchType;

    // ===================== Campos PLD (V28) =====================

    /** Tipo da lista: PEP, SANCTION, CSNU, HIGH_RISK_JURISDICTION, ADVERSE_MEDIA, INTERNAL. */
    @Column(name = "list_type", length = 30)
    private String listType;

    /** Nome encontrado na lista (pode diferir do nome cadastrado da parte). */
    @Column(name = "matched_name")
    private String matchedName;

    /** Dados do hit: aliases, data de nascimento, nacionalidade, programa de sancao etc. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private Map<String, Object> details;

    @Column(name = "review_justification", columnDefinition = "TEXT")
    private String reviewJustification;

    /** Proxima rechecagem prevista. */
    @Column(name = "next_due_at")
    private Instant nextDueAt;

    @Column(length = 20)
    private String status = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
