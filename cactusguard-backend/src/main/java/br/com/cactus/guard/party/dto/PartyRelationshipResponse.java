package br.com.cactus.guard.party.dto;

import br.com.cactus.guard.common.util.DataMasker;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.entity.PartyRelationship;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Relacionamento com a parte relacionada resumida e mascarada (nunca a entity). */
public record PartyRelationshipResponse(
        UUID id,
        RelatedPartySummary relatedParty,
        String relationshipType,
        String description,
        boolean active,
        LocalDate validFrom,
        LocalDate validTo,
        Map<String, Object> details) {

    public record RelatedPartySummary(UUID id, String name, String type, String document,
                                      boolean pep, String pepStatus, String riskLevel) { }

    /** Requer sessao aberta (relatedParty LAZY). */
    public static PartyRelationshipResponse from(PartyRelationship r) {
        Party p = r.getRelatedParty();
        RelatedPartySummary summary = p == null ? null
                : new RelatedPartySummary(p.getId(), p.getName(), p.getType(),
                        DataMasker.maskDocument(p.getDocument()), p.isPep(), p.getPepStatus(), p.getRiskLevel());
        return new PartyRelationshipResponse(r.getId(), summary, r.getRelationshipType(), r.getDescription(),
                r.isActive(), r.getValidFrom(), r.getValidTo(), r.getDetails());
    }
}
