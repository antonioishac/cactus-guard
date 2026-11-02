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
class TransactionCurrencyTest {

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void foreignCurrencyTransactionShouldBeSaved() {
        Party party = Party.builder()
                .type("PF")
                .document("40404040404")
                .name("Currency Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Transaction txn = Transaction.builder()
                .party(party)
                .type("WIRE")
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .status("COMPLETED")
                .transactionDate(Instant.now())
                .build();
        txn = transactionRepository.save(txn);

        assertEquals("USD", txn.getCurrency());
        assertEquals(new BigDecimal("1000.00"), txn.getAmount());
    }
}
