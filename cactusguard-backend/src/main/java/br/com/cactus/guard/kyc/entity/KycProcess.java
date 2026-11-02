package br.com.cactus.guard.kyc.entity;

import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.party.entity.Party;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyc_processes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

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
