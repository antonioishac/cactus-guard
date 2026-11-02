package br.com.cactus.guard.rule;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.engine.RuleEngineService;
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
class TransactionalRuleTest {

    @Autowired
    private RuleEngineService ruleEngineService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void highValueTransactionShouldTriggerRule() {
        Party party = Party.builder()
                .type("PF")
                .document("70707070707")
                .name("High Value Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Transaction txn = Transaction.builder()
                .party(party)
                .type("TED")
                .amount(new BigDecimal("100000.00"))
                .currency("BRL")
                .status("COMPLETED")
                .transactionDate(Instant.now())
                .build();
        transactionRepository.save(txn);

        assertDoesNotThrow(() -> ruleEngineService.evaluateAllActiveRules());
    }
}
