package br.com.cactus.guard.case_.entity;

import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Evidencia anexada ao dossie do caso (V25). Registro imutavel: o sha256 prova que o
 * arquivo nao foi alterado desde o upload (guarda minima de 10 anos).
 */
@Entity
@Table(name = "case_evidences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseEvidence extends br.com.cactus.guard.tenant.TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false, updatable = false)
    private Case caseEntity;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500, updatable = false)
    private String filePath;

    @Column(name = "content_type", length = 100, updatable = false)
    private String contentType;

    /** SHA-256 (hex) do arquivo. */
    @Column(name = "sha256", nullable = false, length = 64, updatable = false)
    private String sha256;

    @Column(name = "size_bytes", updatable = false)
    private Long sizeBytes;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", updatable = false)
    private User uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
