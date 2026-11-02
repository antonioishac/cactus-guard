package br.com.cactus.guard.screening.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "screening_lists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreeningList {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 100)
    private String source;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_updated")
    private Instant lastUpdated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
