package br.com.cactus.guard.kyc.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class KycChecklistResponse {
    private UUID id;
    private UUID processId;
    private String itemCode;
    private String itemDescription;
    private boolean required;
    private boolean completed;
    private Instant completedAt;
}
