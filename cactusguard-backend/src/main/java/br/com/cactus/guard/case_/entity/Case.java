package br.com.cactus.guard.case_.entity;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.party.entity.Party;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Case {

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
    private String status = "OPEN";

    @Column(length = 20)
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
