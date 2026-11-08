package br.com.cactus.guard.party.dto;

import br.com.cactus.guard.common.util.DataMasker;
import br.com.cactus.guard.party.entity.BeneficialOwner;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * UBO/socio. Documento sempre mascarado. {@code ownershipChain} traz so nome e percentual (o
 * {@code document_hash} da cadeia nao e exposto). {@code finalBeneficiary}/{@code indirect} sao os nomes
 * usados pelo front; {@code isFinalBeneficiary}/{@code isIndirect} sao aliases da spec 046.
 */
public record BeneficialOwnerResponse(
        UUID id,
        String ownerDocument,
        String ownerName,
        BigDecimal ownershipPct,
        String controlType,
        boolean pep,
        boolean finalBeneficiary,
        boolean indirect,
        String nationality,
        LocalDate validFrom,
        LocalDate validTo,
        String ownerType,
        UUID ownerPartyId,
        OwnerPartySummary ownerParty,
        List<OwnershipLink> ownershipChain) {

    public record OwnerPartySummary(UUID id, String name) { }

    public record OwnershipLink(String name, Object pct) { }

    @JsonProperty("isFinalBeneficiary")
    public boolean aliasFinalBeneficiary() {
        return finalBeneficiary;
    }

    @JsonProperty("isIndirect")
    public boolean aliasIndirect() {
        return indirect;
    }

    /** Requer sessao aberta (ownerParty LAZY). */
    public static BeneficialOwnerResponse from(BeneficialOwner b) {
        OwnerPartySummary owner = b.getOwnerParty() == null ? null
                : new OwnerPartySummary(b.getOwnerParty().getId(), b.getOwnerParty().getName());
        List<OwnershipLink> chain = b.getOwnershipChain() == null ? null
                : b.getOwnershipChain().stream()
                        .map(m -> new OwnershipLink(m.get("name") == null ? null : String.valueOf(m.get("name")), m.get("pct")))
                        .toList();
        return new BeneficialOwnerResponse(b.getId(), DataMasker.maskDocument(b.getOwnerDocument()), b.getOwnerName(),
                b.getOwnershipPct(), b.getControlType(), b.isPep(), b.isFinalBeneficiary(), b.isIndirect(),
                b.getNationality(), b.getValidFrom(), b.getValidTo(), b.getOwnerType(),
                owner == null ? null : owner.id(), owner, chain);
    }
}
