package br.com.cactus.guard.party.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cadastro/edicao de parte. {@code document} e obrigatorio so na criacao ({@link OnCreate}) e imutavel
 * depois. Em PUT, valores de PII que contenham {@code *} (mascarados, como devolvidos pela API) sao
 * ignorados e o valor atual e mantido.
 */
@Data
public class PartyRequest {
    @NotBlank
    @Pattern(regexp = "PF|PJ", message = "Type must be PF or PJ")
    private String type;

    @NotBlank(groups = OnCreate.class)
    private String document;

    @NotBlank
    private String name;

    private String tradeName;
    private String email;
    private String phone;
    private LocalDate birthDate;
    private String nationality;
    private String occupation;

    // ---- catalogo spec 046 (opcionais; na edicao so os nao nulos sao aplicados)
    private String motherName;
    private String partyRole;
    private LocalDate relationshipStartDate;
    private String countryOfResidence;
    private String cboCode;
    private BigDecimal declaredMonthlyIncome;
    private BigDecimal declaredNetWorth;
    private String sourceOfFunds;
    private String sourceOfWealth;
    private String purposeOfRelationship;
    private BigDecimal expectedMonthlyVolume;
    private LocalDate incorporationDate;
    private String cnaePrimary;
    private List<String> cnaesSecondary;
    private String legalNatureCode;
    private BigDecimal annualRevenue;
    private BigDecimal shareCapital;
    private String companySize;
    private Boolean nonprofit;
    private Boolean publiclyTraded;
}
