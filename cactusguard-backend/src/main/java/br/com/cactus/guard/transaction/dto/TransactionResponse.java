package br.com.cactus.guard.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Transacao. {@code counterpartyDocument} e {@code counterpartyAccount} sempre mascarados.
 * {@code direction} = IN|OUT (ponto de vista da parte). {@code cash} tambem sai como {@code isCash} (spec 046).
 */
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
    private String direction;
    private boolean cash;
    private BigDecimal amountBrl;
    private Instant createdAt;

    private String paymentMethod;
    private String counterpartyCountry;
    private String counterpartyBankIspb;
    private String counterpartyBranch;
    private String counterpartyAccount;
    private String endToEndId;
    private String externalReference;
    private boolean cashWithdrawalProvision;

    @JsonProperty("isCash")
    public boolean aliasIsCash() {
        return cash;
    }
}
