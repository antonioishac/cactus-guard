package br.com.cactus.guard.alert.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
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
    private Instant occurredAt;
    private Instant selectionDeadline;
    private Integer ruleVersion;
    private BigDecimal totalAmount;
    private String falsePositiveReason;
    private Instant createdAt;
}
