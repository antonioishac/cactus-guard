package br.com.cactus.guard.siscoaf.entity;

import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Historico de transicoes de status da comunicacao (tabela siscoaf_history, V9). Append-only. */
@Entity
@Table(name = "siscoaf_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiscoafHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "communication_id", nullable = false, updatable = false)
    private SiscoafCommunication communication;

    @Column(nullable = false, length = 50, updatable = false)
    private String action;

    @Column(name = "from_status", length = 20, updatable = false)
    private String fromStatus;

    @Column(name = "to_status", length = 20, updatable = false)
    private String toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by", updatable = false)
    private User performedBy;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
