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
class AlertInAnalysisCountTest {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void countInAnalysisShouldWork() {
        Party party = Party.builder()
                .type("PF")
                .document("46464646464")
                .name("In Analysis Count")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("HIGH")
                .status("IN_ANALYSIS")
                .title("In Analysis Alert")
                .ruleCode("PEP_MATCH")
                .createdAt(Instant.now())
                .build();
        alertRepository.save(alert);

        long count = alertRepository.countByStatus("IN_ANALYSIS");
        assertTrue(count >= 1);
    }
}
