package br.com.cactus.guard.transaction.entity;

import br.com.cactus.guard.party.entity.Party;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false, unique = true)
    private Party party;

    @Column(name = "avg_monthly_volume", precision = 18, scale = 2)
    private BigDecimal avgMonthlyVolume;

    @Column(name = "avg_monthly_count")
    private Integer avgMonthlyCount;

    @Column(name = "max_single_amount", precision = 18, scale = 2)
    private BigDecimal maxSingleAmount;

    @Column(name = "typical_hours", length = 50)
    private String typicalHours;

    @Column(name = "typical_counterparties")
    private Integer typicalCounterparties;

    @Column(name = "last_calculated_at")
    private Instant lastCalculatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = Instant.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = Instant.now(); }
}
