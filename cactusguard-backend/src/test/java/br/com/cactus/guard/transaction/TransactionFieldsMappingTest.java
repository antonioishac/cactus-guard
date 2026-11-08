package br.com.cactus.guard.transaction;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.transaction.entity.Transaction;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import br.com.cactus.guard.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TransactionFieldsMappingTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void responseShouldIncludeTrackingFields() {
        Party party = Party.builder()
                .type("PF")
                .document("77700077700")
                .documentHash("hash777")
                .name("Transaction Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Transaction tx = Transaction.builder()
                .party(party)
                .type("PIX")
                .amount(BigDecimal.valueOf(1000))
                .currency("BRL")
                .status("COMPLETED")
                .originCountry("BR")
                .destinationCountry("US")
                .purpose("Pagamento de serviço")
                .deviceId("device-123")
                .ipAddress("192.168.1.1")
                .transactionDate(Instant.now())
                .build();
        transactionRepository.save(tx);

        var page = transactionService.findByParty(party.getId(), Pageable.ofSize(10));
        var response = page.getContent().get(0);

        assertEquals("BR", response.getOriginCountry());
        assertEquals("US", response.getDestinationCountry());
        assertEquals("Pagamento de serviço", response.getPurpose());
        assertEquals("device-123", response.getDeviceId());
        assertEquals("192.168.1.1", response.getIpAddress());
    }
}
