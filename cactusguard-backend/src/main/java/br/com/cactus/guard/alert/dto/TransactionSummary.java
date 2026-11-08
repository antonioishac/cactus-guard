package br.com.cactus.guard.alert.dto;

import br.com.cactus.guard.common.util.DataMasker;
import br.com.cactus.guard.transaction.entity.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Transacao resumida para alerta/dossie. Documento da contraparte sempre mascarado. */
public record TransactionSummary(
        UUID id,
        String type,
        BigDecimal amount,
        String currency,
        String direction,
        Instant transactionDate,
        String channel,
        boolean cash,
        String paymentMethod,
        String counterpartyName,
        String counterpartyDocument,
        String counterpartyCountry,
        String status) {

    public static TransactionSummary from(Transaction t) {
        return new TransactionSummary(t.getId(), t.getType(), t.getAmount(), t.getCurrency(), t.getDirection(),
                t.getTransactionDate(), t.getChannel(), t.isCash(), t.getPaymentMethod(), t.getCounterpartyName(),
                t.getCounterpartyDocument() != null ? DataMasker.maskDocument(t.getCounterpartyDocument()) : null,
                t.getCounterpartyCountry(), t.getStatus());
    }
}
