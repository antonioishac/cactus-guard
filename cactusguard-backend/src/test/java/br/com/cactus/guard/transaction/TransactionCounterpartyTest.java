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
class TransactionCounterpartyTest {

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void counterpartyShouldBeSaved() {
        Party party = Party.builder()
                .type("PF")
                .document("66666666666")
                .name("Counterparty Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Transaction txn = Transaction.builder()
                .party(party)
                .type("TED")
                .amount(new BigDecimal("5000.00"))
                .currency("BRL")
                .counterpartyName("Banco ABC")
                .counterpartyDocument("12345678000190")
                .status("COMPLETED")
                .transactionDate(Instant.now())
                .build();
        txn = transactionRepository.save(txn);

        assertEquals("Banco ABC", txn.getCounterpartyName());
        assertEquals("12345678000190", txn.getCounterpartyDocument());
    }
}
