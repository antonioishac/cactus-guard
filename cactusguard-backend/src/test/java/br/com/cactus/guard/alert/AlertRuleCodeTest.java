package br.com.cactus.guard.alert;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AlertRuleCodeTest {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void ruleCodeShouldBeSaved() {
        Party party = Party.builder()
                .type("PF")
                .document("64646464646")
                .name("Rule Code Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("TRANSACTIONAL")
                .severity("HIGH")
                .status("OPEN")
                .title("Rule Code Alert")
                .ruleCode("TXN_HIGH_VALUE")
                .createdAt(Instant.now())
                .build();
        alert = alertRepository.save(alert);

        assertEquals("TXN_HIGH_VALUE", alert.getRuleCode());
    }
}
