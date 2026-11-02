package br.com.cactus.guard.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
public class TransactionRequest {
    @NotNull
    private java.util.UUID partyId;

    @NotBlank
    private String type;

    @NotNull
    @Positive
    private BigDecimal amount;

    private String currency = "BRL";
    private String counterpartyName;
    private String counterpartyDocument;
    private String channel;

    private String originCountry;
    private String destinationCountry;
    private String purpose;
    private String deviceId;
    private String ipAddress;

    @NotNull
    private Instant transactionDate;
}
