package br.com.cactus.guard.party.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
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

    @Column(name = "owner_document", nullable = false, length = 20)
    private String ownerDocument;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(name = "ownership_pct", precision = 5, scale = 2)
    private BigDecimal ownershipPct;

    @Column(name = "control_type", length = 50)
    private String controlType;

    @Column(name = "is_pep")
    private boolean isPep;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = Instant.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = Instant.now(); }
}
