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

    /** IN ou OUT, do ponto de vista da parte. */
    @jakarta.validation.constraints.Pattern(regexp = "IN|OUT", message = "deve ser IN ou OUT")
    private String direction;

    /** Operacao em especie (tambem inferida quando paymentMethod = CASH). */
    private Boolean cash;

    /** PIX, TED, DOC, BOLETO, CASH, CARD, FX, CRYPTO, INTERNAL. */
    @jakarta.validation.constraints.Pattern(regexp = "PIX|TED|DOC|BOLETO|CASH|CARD|FX|CRYPTO|INTERNAL",
            message = "metodo de pagamento invalido")
    private String paymentMethod;

    private String counterpartyCountry;

    /** Valor convertido em reais (obrigatorio para analise de moeda estrangeira em especie). */
    private BigDecimal amountBrl;

    /** Provisionamento de saque em especie. */
    private Boolean cashWithdrawalProvision;

    private String externalReference;

    /** Identificador fim-a-fim do PIX. */
    private String endToEndId;

    private String counterpartyBankIspb;
    private String counterpartyBranch;
    private String counterpartyAccount;

    @NotNull
    private Instant transactionDate;
}
