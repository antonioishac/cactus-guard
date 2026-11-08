package br.com.cactus.guard.party.entity;

import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "beneficial_owners")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BeneficialOwner {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    // Coluna alargada para VARCHAR(512) em V21 (permite criptografia futura).
    @Column(name = "owner_document", nullable = false, length = 512)
    private String ownerDocument;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(name = "ownership_pct", precision = 5, scale = 2)
    private BigDecimal ownershipPct;

    @Column(name = "control_type", length = 50)
    private String controlType;

    @Column(name = "is_pep")
    private boolean isPep;

    // ===================== Campos PLD (V23) =====================

    /** Parte cadastrada correspondente ao socio/UBO (quando existir). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_party_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Party ownerParty;

    /** PF ou PJ (cadeia societaria possui PJ intermediarias). */
    @Column(name = "owner_type", length = 2)
    private String ownerType;

    @Column(name = "owner_document_hash", length = 64)
    private String ownerDocumentHash;

    @Column(name = "is_final_beneficiary", nullable = false)
    @Builder.Default
    private boolean finalBeneficiary = true;

    @Column(name = "is_indirect", nullable = false)
    @Builder.Default
    private boolean indirect = false;

    /** Cadeia de participacao do topo ate a PF final: [{"document_hash","name","pct"}, ...]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ownership_chain")
    private List<Map<String, Object>> ownershipChain;

    @Column(length = 50)
    private String nationality;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User verifiedBy;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = Instant.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = Instant.now(); }
}
