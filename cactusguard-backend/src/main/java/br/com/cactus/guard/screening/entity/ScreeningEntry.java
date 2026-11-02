package br.com.cactus.guard.screening.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "screening_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreeningEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", nullable = false)
    private ScreeningList list;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(length = 50)
    private String document;

    @Column(name = "document_type", length = 20)
    private String documentType;

    @Column(length = 3)
    private String country;

    @Column(name = "pep_category", length = 50)
    private String pepCategory;

    @Column(name = "pep_role")
    private String pepRole;

    @Column(name = "pep_start_date")
    private LocalDate pepStartDate;

    @Column(name = "pep_end_date")
    private LocalDate pepEndDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
