package br.com.cactus.guard.device.entity;

import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Somente leitura: evita inicializar o proxy de User fora de sessao. */
    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 1024)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DevicePlatform platform;

    @Column(name = "app_version", length = 30)
    private String appVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
    }
}
