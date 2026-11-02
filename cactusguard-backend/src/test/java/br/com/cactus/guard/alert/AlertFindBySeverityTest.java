package br.com.cactus.guard.alert;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.alert.service.AlertService;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AlertFindBySeverityTest {

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void findBySeverityShouldReturnAlerts() {
        Party party = Party.builder()
                .type("PF")
                .document("42424242424")
                .name("Severity Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("CRITICAL")
                .status("OPEN")
                .title("Critical Alert")
                .ruleCode("SANCTION_MATCH")
                .createdAt(Instant.now())
                .build();
        alertRepository.save(alert);

        var alerts = alertService.findBySeverity("CRITICAL", org.springframework.data.domain.Pageable.unpaged());
        assertFalse(alerts.isEmpty());
    }
}
