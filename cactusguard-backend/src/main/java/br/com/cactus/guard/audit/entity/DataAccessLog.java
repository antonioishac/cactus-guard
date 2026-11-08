package br.com.cactus.guard.audit.entity;

import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@org.hibernate.annotations.Immutable // append-only: Hibernate nunca emite UPDATE (trigger V22 rejeita)
@Table(name = "data_access_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "fields_accessed")
    private String fieldsAccessed;

    @Column(name = "access_reason", length = 500)
    private String accessReason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() { createdAt = Instant.now(); }
}
