package br.com.cactus.guard.screening.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ScreeningResultResponse {
    private UUID id;
    private UUID partyId;
    private String listCode;
    private String matchedName;
    private String matchedDocument;
    private BigDecimal matchScore;
    private String matchType;
    private String status;
    private Instant createdAt;
    private String partyName;
    /** Lista exige indisponibilidade de ativos (CSNU): confirmar bloqueia a parte imediatamente. */
    private boolean requiresAssetFreeze;
    private String reviewJustification;
    private Instant reviewedAt;
    private String reviewedByName;
}
