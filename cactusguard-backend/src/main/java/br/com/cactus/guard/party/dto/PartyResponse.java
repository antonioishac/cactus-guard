package br.com.cactus.guard.party.dto;

import br.com.cactus.guard.common.util.DataMasker;
import br.com.cactus.guard.party.entity.Party;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Ficha da parte (spec 046). PII (document, email, phone, motherName) sempre mascarada via {@link DataMasker}. */
@Data
@Builder
public class PartyResponse {

    private UUID id;
    private String type;
    private String document;
    private String name;
    private String tradeName;
    private String email;
    private String phone;
    private String riskLevel;
    private LocalDate birthDate;
    private String nationality;
    private String occupation;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    // papel / origem
    private String partyRole;
    private String sourceType;
    private LocalDate relationshipStartDate;
    private LocalDate relationshipEndDate;

    // PF
    private String motherName;
    private String countryOfResidence;
    private String taxResidenceCountry;
    private String cboCode;
    private BigDecimal declaredMonthlyIncome;
    private BigDecimal declaredNetWorth;
    private String sourceOfFunds;
    private String sourceOfWealth;
    private String purposeOfRelationship;
    private BigDecimal expectedMonthlyVolume;

    // PJ
    private String cnaePrimary;
    private List<String> cnaesSecondary;
    private String legalNatureCode;
    private LocalDate incorporationDate;
    private BigDecimal shareCapital;
    private BigDecimal annualRevenue;
    private String companySize;
    private boolean nonprofit;
    private boolean publiclyTraded;
    private Boolean uboIdentified;
    private String uboNotIdentifiedReason;

    // Receita / KYC
    private String receitaStatus;
    private Instant receitaCheckedAt;
    private Instant kycLastUpdateAt;

    // PEP
    private boolean pep;
    private String pepStatus;
    private String pepCategory;
    private String pepRole;
    private LocalDate pepStartDate;
    private LocalDate pepEndDate;
    private LocalDate pepExpiryDate;
    private Instant pepApprovalAt;
    private String pepApprovalByName;

    // risco / EDD
    private BigDecimal riskScore;
    private Instant riskCalculatedAt;
    private LocalDate nextReviewDate;
    private boolean eddRequired;

    // indisponibilidade de ativos (Lei 13.810/2019)
    private boolean assetFreezeActive;
    private Instant assetFreezeAt;
    private String assetFreezeReference;

    /** Requer sessao/transacao aberta (le o aprovador de PEP, associacao LAZY). */
    public static PartyResponse from(Party p, boolean maskSensitive) {
        return PartyResponse.builder()
                .id(p.getId())
                .type(p.getType())
                .document(maskSensitive ? DataMasker.maskDocument(p.getDocument()) : p.getDocument())
                .name(p.getName())
                .tradeName(p.getTradeName())
                .email(p.getEmail() == null ? null : maskSensitive ? DataMasker.maskEmail(p.getEmail()) : p.getEmail())
                .phone(p.getPhone() == null ? null : maskSensitive ? DataMasker.maskPhone(p.getPhone()) : p.getPhone())
                .riskLevel(p.getRiskLevel())
                .birthDate(p.getBirthDate())
                .nationality(p.getNationality())
                .occupation(p.getOccupation())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .partyRole(p.getPartyRole())
                .sourceType(p.getSourceType())
                .relationshipStartDate(p.getRelationshipStartDate())
                .relationshipEndDate(p.getRelationshipEndDate())
                .motherName(p.getMotherName() == null ? null
                        : maskSensitive ? DataMasker.maskName(p.getMotherName()) : p.getMotherName())
                .countryOfResidence(p.getCountryOfResidence())
                .taxResidenceCountry(p.getTaxResidenceCountry())
                .cboCode(p.getCboCode())
                .declaredMonthlyIncome(p.getDeclaredMonthlyIncome())
                .declaredNetWorth(p.getDeclaredNetWorth())
                .sourceOfFunds(p.getSourceOfFunds())
                .sourceOfWealth(p.getSourceOfWealth())
                .purposeOfRelationship(p.getPurposeOfRelationship())
                .expectedMonthlyVolume(p.getExpectedMonthlyVolume())
                .cnaePrimary(p.getCnaePrimary())
                .cnaesSecondary(p.getCnaesSecondary())
                .legalNatureCode(p.getLegalNatureCode())
                .incorporationDate(p.getIncorporationDate())
                .shareCapital(p.getShareCapital())
                .annualRevenue(p.getAnnualRevenue())
                .companySize(p.getCompanySize())
                .nonprofit(p.isNonprofit())
                .publiclyTraded(p.isPubliclyTraded())
                .uboIdentified(p.getUboIdentified())
                .uboNotIdentifiedReason(p.getUboNotIdentifiedReason())
                .receitaStatus(p.getReceitaStatus())
                .receitaCheckedAt(p.getReceitaCheckedAt())
                .kycLastUpdateAt(p.getKycLastUpdateAt())
                .pep(p.isPep())
                .pepStatus(p.getPepStatus())
                .pepCategory(p.getPepCategory())
                .pepRole(p.getPepRole())
                .pepStartDate(p.getPepStartDate())
                .pepEndDate(p.getPepEndDate())
                .pepExpiryDate(p.getPepExpiryDate())
                .pepApprovalAt(p.getPepApprovalAt())
                .pepApprovalByName(p.getPepApprovalBy() != null ? p.getPepApprovalBy().getName() : null)
                .riskScore(p.getRiskScore())
                .riskCalculatedAt(p.getRiskCalculatedAt())
                .nextReviewDate(p.getNextReviewDate())
                .eddRequired(p.isEddRequired())
                .assetFreezeActive(p.isAssetFreezeActive())
                .assetFreezeAt(p.getAssetFreezeAt())
                .assetFreezeReference(p.getAssetFreezeReference())
                .build();
    }
}
