package br.com.cactus.guard.device.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_push_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushPreference extends br.com.cactus.guard.tenant.TenantScoped {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "critical_alerts", nullable = false)
    private boolean criticalAlerts;

    @Column(nullable = false)
    private boolean deadlines;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
