package br.com.cactus.guard.alert.dto;

import br.com.cactus.guard.common.util.DataMasker;
import br.com.cactus.guard.party.entity.Party;

import java.math.BigDecimal;
import java.util.UUID;

/** Dados resumidos da parte para alerta/dossie (documento mascarado). */
public record PartySummary(
        UUID id,
        String name,
        String type,
        String document,
        String riskLevel,
        BigDecimal riskScore,
        boolean pep,
        String pepStatus,
        boolean eddRequired,
        boolean assetFreezeActive,
        String status) {

    public static PartySummary from(Party p) {
        return new PartySummary(p.getId(), p.getName(), p.getType(), DataMasker.maskDocument(p.getDocument()),
                p.getRiskLevel(), p.getRiskScore(), p.isPep(), p.getPepStatus(), p.isEddRequired(),
                p.isAssetFreezeActive(), p.getStatus());
    }
}
