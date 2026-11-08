package br.com.cactus.guard.case_.entity;

import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Nota de analise do case (tabela case_notes, V4). Imutavel: nao ha edicao nem exclusao. */
@Entity
@Table(name = "case_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false, updatable = false)
    private Case caseEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", updatable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
