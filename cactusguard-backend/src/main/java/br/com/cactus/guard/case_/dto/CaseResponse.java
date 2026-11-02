package br.com.cactus.guard.case_.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CaseResponse {
    private UUID id;
    private UUID alertId;
    private UUID partyId;
    private String partyName;
    private String status;
    private String priority;
    private UUID assignedToId;
    private String decision;
    private String decisionJustification;
    private Instant decidedAt;
    private Instant slaDueDate;
    private Instant createdAt;
}
