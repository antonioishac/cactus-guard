package br.com.cactus.guard.transaction.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {
    private UUID id;
    private UUID partyId;
    private String partyName;
    private String type;
    private BigDecimal amount;
    private String currency;
    private String counterpartyName;
    private String counterpartyDocument;
    private String channel;
    private String status;
    private Instant transactionDate;
    private String originCountry;
    private String destinationCountry;
    private String purpose;
    private String deviceId;
    private String ipAddress;
    private Instant createdAt;
}
