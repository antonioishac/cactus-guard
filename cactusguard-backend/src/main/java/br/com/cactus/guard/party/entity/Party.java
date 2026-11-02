package br.com.cactus.guard.party.entity;

import br.com.cactus.guard.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String type; // PF or PJ

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, unique = true, length = 500)
    private String document;

    @Column(name = "document_hash", length = 64)
    private String documentHash;

    @Column(nullable = false)
    private String name;

    @Column(name = "trade_name")
    private String tradeName;

    @Convert(converter = EncryptedStringConverter.class)
    private String email;

    @Convert(converter = EncryptedStringConverter.class)
    private String phone;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "birth_date")
    private java.time.LocalDate birthDate;

    @Column(length = 50)
    private String nationality;

    @Column(length = 100)
    private String occupation;

    @Column(name = "search_vector", columnDefinition = "tsvector")
    private String searchVector;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
