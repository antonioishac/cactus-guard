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
class TransactionVelocityTest {

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void multipleTransactionsShouldBeCountable() {
        Party party = Party.builder()
                .type("PF")
                .document("14141414141")
                .name("Velocity Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        for (int i = 0; i < 5; i++) {
            Transaction txn = Transaction.builder()
                    .party(party)
                    .type("PIX")
                    .amount(new BigDecimal("100.00"))
                    .currency("BRL")
                    .status("COMPLETED")
                    .transactionDate(Instant.now())
                    .build();
            transactionRepository.save(txn);
        }

        long count = transactionRepository.countByPartySince(party.getId(), Instant.now().minusSeconds(3600));
        assertEquals(5, count);
    }
}
