package br.com.cactus.guard.transaction;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.transaction.dto.TransactionRequest;
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
class TransactionServiceTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void createTransactionViaServiceShouldWork() {
        Party party = Party.builder()
                .type("PF")
                .document("21212121212")
                .name("Service Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        TransactionRequest request = new TransactionRequest();
        request.setPartyId(party.getId());
        request.setType("TED");
        request.setAmount(new BigDecimal("25000.00"));
        request.setTransactionDate(Instant.now());
        request.setCounterpartyName("Banco XYZ");

        var response = transactionService.create(request);
        assertNotNull(response.getId());
        assertEquals(new BigDecimal("25000.00"), response.getAmount());
        assertEquals("TED", response.getType());
    }
}
