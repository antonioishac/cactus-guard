package br.com.cactus.guard.kyc.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class KycDocumentResponse {
    private UUID id;
    private UUID processId;
    private String documentType;
    private String fileName;
    private String status;
    private Instant createdAt;
}
