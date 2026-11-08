package br.com.cactus.guard.transaction.service;

import br.com.cactus.guard.transaction.repository.TransactionRepository;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.transaction.dto.TransactionRequest;
import br.com.cactus.guard.transaction.dto.TransactionResponse;
import br.com.cactus.guard.transaction.entity.Transaction;
import br.com.cactus.guard.audit.service.DataAccessService;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.event.dto.DomainEventType;
import br.com.cactus.guard.event.service.DomainEventPublisher;
import br.com.cactus.guard.event.service.EventPayloads;
import br.com.cactus.guard.common.util.DataMasker;
import br.com.cactus.guard.common.util.DocumentHash;
import br.com.cactus.guard.siscoaf.service.CashCommunicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final PartyRepository partyRepository;
    private final DataAccessService dataAccessService;
    private final CashCommunicationService cashCommunication;
    private final AssetFreezeGuard assetFreezeGuard;
    private final DomainEventPublisher events;

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        Party party = partyRepository.findById(request.getPartyId())
                .orElseThrow(() -> new IllegalArgumentException("Party not found"));

        // Lei 13.810/2019: parte com ativos indisponiveis nao movimenta (somente entrada explicita IN e aceita)
        if (party.isAssetFreezeActive() && !"IN".equals(request.getDirection())) {
            assetFreezeGuard.registerBlockedAttempt(party, request.getType(), request.getAmount(), request.getDirection());
            throw new BusinessException("Operação rejeitada por restrição de conformidade");
        }

        String currency = request.getCurrency() != null ? request.getCurrency() : "BRL";
        boolean cash = Boolean.TRUE.equals(request.getCash()) || "CASH".equals(request.getPaymentMethod());
        BigDecimal amountBrl = request.getAmountBrl() != null ? request.getAmountBrl()
                : ("BRL".equals(currency) ? request.getAmount() : null);

        Transaction txn = Transaction.builder()
                .party(party)
                .type(request.getType())
                .amount(request.getAmount())
                .currency(currency)
                .counterpartyName(request.getCounterpartyName())
                .counterpartyDocument(request.getCounterpartyDocument())
                .counterpartyDocumentHash(request.getCounterpartyDocument() != null
                        ? DocumentHash.of(request.getCounterpartyDocument()) : null)
                .channel(request.getChannel())
                .originCountry(request.getOriginCountry())
                .destinationCountry(request.getDestinationCountry())
                .counterpartyCountry(request.getCounterpartyCountry())
                .purpose(request.getPurpose())
                .deviceId(request.getDeviceId())
                .ipAddress(request.getIpAddress())
                .direction(request.getDirection())
                .cash(cash)
                .paymentMethod(request.getPaymentMethod())
                .amountBrl(amountBrl)
                .cashWithdrawalProvision(Boolean.TRUE.equals(request.getCashWithdrawalProvision()))
                .externalReference(request.getExternalReference())
                .endToEndId(request.getEndToEndId())
                .counterpartyBankIspb(request.getCounterpartyBankIspb())
                .counterpartyBranch(request.getCounterpartyBranch())
                .counterpartyAccount(request.getCounterpartyAccount())
                .status("COMPLETED")
                .transactionDate(request.getTransactionDate())
                .build();

        txn = transactionRepository.save(txn);
        // Excecao legal: especie >= limiar gera COE sem decisao de suspeita (auditada)
        cashCommunication.evaluate(txn);
        // Evento na mesma transacao (outbox): nenhum evento se perde mesmo com o Kafka fora do ar
        events.publish(DomainEventType.TRANSACTION_RECEIVED, txn.getId(), EventPayloads.transactionReceived(txn));
        return toResponse(txn);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> findByParty(UUID partyId, Pageable pageable) {
        dataAccessService.log("TRANSACTION", partyId, "amount,type", "CONSULTA", null);
        return transactionRepository.findByPartyId(partyId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse findById(UUID id) {
        Transaction txn = transactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        return toResponse(txn);
    }

    private TransactionResponse toResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .partyId(t.getParty().getId())
                .partyName(t.getParty().getName())
                .type(t.getType())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .counterpartyName(t.getCounterpartyName())
                .counterpartyDocument(t.getCounterpartyDocument() == null ? null : DataMasker.maskDocument(t.getCounterpartyDocument()))
                .channel(t.getChannel())
                .status(t.getStatus())
                .transactionDate(t.getTransactionDate())
                .originCountry(t.getOriginCountry())
                .destinationCountry(t.getDestinationCountry())
                .purpose(t.getPurpose())
                .deviceId(t.getDeviceId())
                .ipAddress(t.getIpAddress())
                .direction(t.getDirection())
                .cash(t.isCash())
                .amountBrl(t.getAmountBrl())
                .paymentMethod(t.getPaymentMethod())
                .counterpartyCountry(t.getCounterpartyCountry())
                .counterpartyBankIspb(t.getCounterpartyBankIspb())
                .counterpartyBranch(t.getCounterpartyBranch())
                .counterpartyAccount(t.getCounterpartyAccount() == null ? null : DataMasker.maskDocument(t.getCounterpartyAccount()))
                .endToEndId(t.getEndToEndId())
                .externalReference(t.getExternalReference())
                .cashWithdrawalProvision(t.isCashWithdrawalProvision())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
