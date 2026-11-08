package br.com.cactus.guard.kyc.entity;

import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyc_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false)
    private KycProcess process;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    // BYTEA no PostgreSQL (V21). Sem @Lob: no PostgreSQL o Hibernate mapearia @Lob byte[] para OID.
    @Column(name = "content", columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "content_type", length = 100)
    private String contentType;

    /** SHA-256 (hex) do conteudo - prova de integridade da evidencia documental (V21). */
    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(length = 20)
    private String status = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
