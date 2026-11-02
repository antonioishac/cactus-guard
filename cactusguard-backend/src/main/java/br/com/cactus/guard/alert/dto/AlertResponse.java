package br.com.cactus.guard.alert.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AlertResponse {
    private UUID id;
    private UUID partyId;
    private String partyName;
    private String type;
    private String severity;
    private String status;
    private String title;
    private String description;
    private String ruleCode;
    private UUID assignedToId;
    private Instant dueDate;
    private Instant createdAt;
}
