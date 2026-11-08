package br.com.cactus.guard.risk.entity;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskHistory extends br.com.cactus.guard.tenant.TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Column(name = "previous_score", precision = 5, scale = 2)
    private BigDecimal previousScore;

    @Column(name = "new_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal newScore;

    @Column(name = "previous_level", length = 20)
    private String previousLevel;

    @Column(name = "new_level", nullable = false, length = 20)
    private String newLevel;

    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() { createdAt = Instant.now(); }
}
