package br.com.cactus.guard.kyc.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class KycProcessResponse {
    private UUID id;
    private UUID partyId;
    private String partyName;
    private String type;
    private String status;
    private String riskLevel;
    private Instant startedAt;
    private Instant completedAt;
    private Instant expiresAt;
    private Instant createdAt;
}
