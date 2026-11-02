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
class TransactionFindByPartyTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void findByPartyShouldReturnTransactions() {
        Party party = Party.builder()
                .type("PF")
                .document("32323232323")
                .name("Find By Party Txn")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        var request = new br.com.cactus.guard.transaction.dto.TransactionRequest();
        request.setPartyId(party.getId());
        request.setType("PIX");
        request.setAmount(new BigDecimal("500.00"));
        request.setTransactionDate(Instant.now());

        transactionService.create(request);

        var page = transactionService.findByParty(party.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertFalse(page.isEmpty());
    }
}
