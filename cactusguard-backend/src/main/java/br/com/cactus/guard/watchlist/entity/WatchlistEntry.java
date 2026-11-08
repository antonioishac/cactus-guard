package br.com.cactus.guard.watchlist.entity;

import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "watchlist_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String document;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false, length = 20)
    private String severity = "HIGH";

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private User addedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() { createdAt = Instant.now(); }
}
