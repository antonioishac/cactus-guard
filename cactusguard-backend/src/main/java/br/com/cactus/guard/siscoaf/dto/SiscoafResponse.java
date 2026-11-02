package br.com.cactus.guard.siscoaf.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class SiscoafResponse {
    private UUID id;
    private UUID caseId;
    private UUID partyId;
    private String type;
    private String status;
    private String protocolNumber;
    private Instant submittedAt;
    private Map<String, Object> payload;
    private Instant createdAt;
}
