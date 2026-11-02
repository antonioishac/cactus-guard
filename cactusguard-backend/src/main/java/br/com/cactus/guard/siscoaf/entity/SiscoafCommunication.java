package br.com.cactus.guard.siscoaf.entity;

import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.party.entity.Party;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "siscoaf_communications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiscoafCommunication {

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
