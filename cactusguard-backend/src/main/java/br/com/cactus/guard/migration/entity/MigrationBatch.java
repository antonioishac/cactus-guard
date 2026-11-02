package br.com.cactus.guard.migration.entity;

import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "migration_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_system", nullable = false, length = 100)
    private String sourceSystem;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "total_records")
    private Integer totalRecords = 0;

    @Column(name = "processed_records")
    private Integer processedRecords = 0;

    @Column(name = "failed_records")
    private Integer failedRecords = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
