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
class AlertFalsePositiveTest {

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    @WithMockUser(username = "admin", roles = "ANALYST")
    void markFalsePositiveShouldCloseAlert() {
        Party party = Party.builder()
                .type("PF")
                .document("23232323232")
                .name("False Positive Alert")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("LOW")
                .status("OPEN")
                .title("FP Alert")
                .ruleCode("UNUSUAL_PATTERN")
                .createdAt(Instant.now())
                .build();
        alert = alertRepository.save(alert);

        var response = alertService.markFalsePositive(alert.getId(), "Falso positivo confirmado");
        assertEquals("FALSE_POSITIVE", response.getStatus());
    }
}
