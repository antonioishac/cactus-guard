package br.com.cactus.guard.transaction;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.transaction.entity.Transaction;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TransactionStatusTest {

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void pendingTransactionShouldBeSaved() {
        Party party = Party.builder()
                .type("PF")
                .document("44444444444")
                .name("Status Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Transaction txn = Transaction.builder()
                .party(party)
                .type("TED")
                .amount(new BigDecimal("1500.00"))
                .currency("BRL")
                .status("PENDING")
                .transactionDate(Instant.now())
                .build();
        txn = transactionRepository.save(txn);

        assertEquals("PENDING", txn.getStatus());
    }
}
