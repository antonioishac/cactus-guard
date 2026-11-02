package br.com.cactus.guard.transaction;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TransactionFindByIdTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void findByIdShouldReturnTransaction() {
        Party party = Party.builder()
                .type("PF")
                .document("50505050505")
                .name("Find By Id Txn")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        var request = new br.com.cactus.guard.transaction.dto.TransactionRequest();
        request.setPartyId(party.getId());
        request.setType("PIX");
        request.setAmount(new BigDecimal("750.00"));
        request.setTransactionDate(Instant.now());

        var created = transactionService.create(request);
        var found = transactionService.findById(created.getId());

        assertEquals(created.getId(), found.getId());
        assertEquals(new BigDecimal("750.00"), found.getAmount());
    }
}
