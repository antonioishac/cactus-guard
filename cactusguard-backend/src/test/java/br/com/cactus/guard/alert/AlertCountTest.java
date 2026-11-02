package br.com.cactus.guard.alert;

import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.alert.entity.Alert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AlertCountTest {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void countByStatusShouldWork() {
        Party party = Party.builder()
                .type("PF")
                .document("28282828282")
                .name("Count Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("MEDIUM")
                .status("OPEN")
                .title("Count Alert")
                .ruleCode("PEP_MATCH")
                .createdAt(Instant.now())
                .build();
        alertRepository.save(alert);

        long open = alertRepository.countByStatus("OPEN");
        assertTrue(open >= 1);
    }
}
