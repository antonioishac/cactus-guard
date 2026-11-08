package br.com.cactus.guard.transaction.service;

import br.com.cactus.guard.transaction.repository.TransactionRepository;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.transaction.dto.TransactionRequest;
import br.com.cactus.guard.transaction.dto.TransactionResponse;
import br.com.cactus.guard.transaction.entity.Transaction;
import br.com.cactus.guard.audit.service.DataAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final PartyRepository partyRepository;
    private final DataAccessService dataAccessService;

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        Party party = partyRepository.findById(request.getPartyId())
                .orElseThrow(() -> new IllegalArgumentException("Party not found"));

        Transaction txn = Transaction.builder()
                .party(party)
                .type(request.getType())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "BRL")
                .counterpartyName(request.getCounterpartyName())
                .counterpartyDocument(request.getCounterpartyDocument())
                .channel(request.getChannel())
                .originCountry(request.getOriginCountry())
                .destinationCountry(request.getDestinationCountry())
                .purpose(request.getPurpose())
                .deviceId(request.getDeviceId())
                .ipAddress(request.getIpAddress())
                .status("COMPLETED")
                .transactionDate(request.getTransactionDate())
                .build();

        txn = transactionRepository.save(txn);
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
                .counterpartyDocument(t.getCounterpartyDocument())
                .channel(t.getChannel())
                .status(t.getStatus())
                .transactionDate(t.getTransactionDate())
                .originCountry(t.getOriginCountry())
                .destinationCountry(t.getDestinationCountry())
                .purpose(t.getPurpose())
                .deviceId(t.getDeviceId())
                .ipAddress(t.getIpAddress())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
