package br.com.cactus.guard.party.entity;

import br.com.cactus.guard.crypto.EncryptedStringConverter;
import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "parties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Party extends br.com.cactus.guard.tenant.TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String type; // PF or PJ

    // Ciphertext AES-GCM com IV aleatorio: UNIQUE aqui seria inutil. A unicidade e
    // garantida por document_hash (indice unico uq_parties_document_hash, V21).
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 512)
    private String document;

    @Column(name = "document_hash", length = 64)
    private String documentHash;

    @Column(nullable = false)
    private String name;

    @Column(name = "trade_name")
    private String tradeName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 512)
    private String email;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 512)
    private String phone;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "birth_date")
    private java.time.LocalDate birthDate;

    @Column(length = 50)
    private String nationality;

    @Column(length = 100)
    private String occupation;

    // Mantido pelo trigger parties_search_vector_update (V14); nunca escrito pela aplicacao
    @Column(name = "search_vector", columnDefinition = "tsvector", insertable = false, updatable = false)
    private String searchVector;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    // ===================== Papel / origem do cadastro (V23) =====================

    /** CUSTOMER, UBO, COUNTERPARTY, EMPLOYEE, PARTNER, SUPPLIER, REPRESENTATIVE (KYC/KYE/KYP/KYS). */
    @Column(name = "party_role", nullable = false, length = 20)
    @Builder.Default
    private String partyRole = "CUSTOMER";

    /** MIGRATED ou ONBOARDED. */
    @Column(name = "source_type", nullable = false, length = 20)
    @Builder.Default
    private String sourceType = "ONBOARDED";

    @Column(name = "relationship_start_date")
    private LocalDate relationshipStartDate;

    @Column(name = "relationship_end_date")
    private LocalDate relationshipEndDate;

    @Column(name = "onboarding_channel", length = 30)
    private String onboardingChannel;

    // ===================== PEP (Circular 3.978 arts. 19, 27, 34) =====================

    @Column(name = "is_pep", nullable = false)
    @Builder.Default
    private boolean pep = false;

    /** NONE, ACTIVE, EXPIRED, RELATED. */
    @Column(name = "pep_status", nullable = false, length = 20)
    @Builder.Default
    private String pepStatus = "NONE";

    /** NATIONAL, FOREIGN, INTERNATIONAL_ORG. */
    @Column(name = "pep_category", length = 30)
    private String pepCategory;

    @Column(name = "pep_role")
    private String pepRole;

    @Column(name = "pep_start_date")
    private LocalDate pepStartDate;

    @Column(name = "pep_end_date")
    private LocalDate pepEndDate;

    /** pep_end_date + 5 anos. */
    @Column(name = "pep_expiry_date")
    private LocalDate pepExpiryDate;

    /** Aprovacao da alta administracao para iniciar/manter relacionamento com PEP (art. 34). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pep_approval_by")
    private User pepApprovalBy;

    @Column(name = "pep_approval_at")
    private Instant pepApprovalAt;

    // ===================== Risco =====================

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "risk_calculated_at")
    private Instant riskCalculatedAt;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "edd_required", nullable = false)
    @Builder.Default
    private boolean eddRequired = false;

    // ===================== Pessoa fisica =====================

    /** Criptografado: desambiguacao de homonimos e fator de autenticacao sensivel. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "mother_name", length = 512)
    private String motherName;

    @Column(name = "country_of_residence", length = 3)
    private String countryOfResidence;

    @Column(name = "tax_residence_country", length = 3)
    private String taxResidenceCountry;

    @Column(name = "id_document_type", length = 20)
    private String idDocumentType;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "id_document_number", length = 512)
    private String idDocumentNumber;

    @Column(name = "id_document_issuer", length = 30)
    private String idDocumentIssuer;

    @Column(name = "cbo_code", length = 10)
    private String cboCode;

    @Column(name = "declared_monthly_income", precision = 18, scale = 2)
    private BigDecimal declaredMonthlyIncome;

    @Column(name = "declared_net_worth", precision = 18, scale = 2)
    private BigDecimal declaredNetWorth;

    @Column(name = "source_of_funds", length = 500)
    private String sourceOfFunds;

    @Column(name = "source_of_wealth", length = 500)
    private String sourceOfWealth;

    @Column(name = "purpose_of_relationship", length = 500)
    private String purposeOfRelationship;

    @Column(name = "expected_monthly_volume", precision = 18, scale = 2)
    private BigDecimal expectedMonthlyVolume;

    // ===================== Pessoa juridica =====================

    @Column(name = "cnae_primary", length = 10)
    private String cnaePrimary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cnaes_secondary")
    private List<String> cnaesSecondary;

    @Column(name = "legal_nature_code", length = 10)
    private String legalNatureCode;

    @Column(name = "incorporation_date")
    private LocalDate incorporationDate;

    @Column(name = "share_capital", precision = 18, scale = 2)
    private BigDecimal shareCapital;

    @Column(name = "annual_revenue", precision = 18, scale = 2)
    private BigDecimal annualRevenue;

    /** MEI, ME, EPP, DEMAIS. */
    @Column(name = "company_size", length = 20)
    private String companySize;

    @Column(name = "is_nonprofit", nullable = false)
    @Builder.Default
    private boolean nonprofit = false;

    @Column(name = "is_publicly_traded", nullable = false)
    @Builder.Default
    private boolean publiclyTraded = false;

    /** NULL = nao avaliado (ou PF). */
    @Column(name = "ubo_identified")
    private Boolean uboIdentified;

    @Column(name = "ubo_not_identified_reason", columnDefinition = "TEXT")
    private String uboNotIdentifiedReason;

    // ===================== Receita Federal / KYC =====================

    /** REGULAR, SUSPENSA, CANCELADA, PENDENTE, NULA, TITULAR_FALECIDO, BAIXADA, INAPTA, ATIVA. */
    @Column(name = "receita_status", length = 20)
    private String receitaStatus;

    @Column(name = "receita_checked_at")
    private Instant receitaCheckedAt;

    @Column(name = "kyc_last_update_at")
    private Instant kycLastUpdateAt;

    // ===================== Indisponibilidade de ativos (Lei 13.810/2019) =====================

    @Column(name = "asset_freeze_active", nullable = false)
    @Builder.Default
    private boolean assetFreezeActive = false;

    @Column(name = "asset_freeze_at")
    private Instant assetFreezeAt;

    @Column(name = "asset_freeze_reference")
    private String assetFreezeReference;

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
