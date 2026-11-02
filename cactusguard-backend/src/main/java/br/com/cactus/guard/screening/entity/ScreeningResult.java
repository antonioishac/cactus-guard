package br.com.cactus.guard.screening.entity;

import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.party.entity.Party;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
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
