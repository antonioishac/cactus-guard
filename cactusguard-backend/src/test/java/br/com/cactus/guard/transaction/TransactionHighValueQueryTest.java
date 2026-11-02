package br.com.cactus.guard.transaction;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import br.com.cactus.guard.transaction.entity.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TransactionHighValueQueryTest {

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void highValueQueryShouldFindTransactions() {
        Party party = Party.builder()
                .type("PF")
                .document("26262626262")
                .name("High Value Query")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Transaction txn = Transaction.builder()
                .party(party)
                .type("TED")
                .amount(new BigDecimal("80000.00"))
                .currency("BRL")
                .status("COMPLETED")
                .transactionDate(Instant.now())
                .build();
        transactionRepository.save(txn);

        var results = transactionRepository.findHighValueByParty(
                party.getId(), new BigDecimal("50000"), Instant.now().minusSeconds(86400));
        assertFalse(results.isEmpty());
        assertEquals(new BigDecimal("80000.00"), results.get(0).getAmount());
    }
}
