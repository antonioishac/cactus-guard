package br.com.cactus.guard.screening.entity;

import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Jurisdicao de alto risco (V28): listas do GAFI (call for action / monitoramento
 * reforcado), paises de tributacao favorecida e lista interna. Vigencia por periodo
 * para reconstrucao historica (a lista cinza muda a cada plenaria do GAFI).
 */
@Entity
@Table(name = "high_risk_jurisdictions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HighRiskJurisdiction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** ISO 3166-1 alpha-2. */
    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Column(name = "country_name", length = 100)
    private String countryName;

    /** GAFI_BLACK, GAFI_GREY, INTERNAL, TAX_HAVEN. */
    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(columnDefinition = "TEXT")
    private String notes;

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
