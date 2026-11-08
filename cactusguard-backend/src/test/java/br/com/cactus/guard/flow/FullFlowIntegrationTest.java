package br.com.cactus.guard.flow;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.service.CaseService;
import br.com.cactus.guard.notification.repository.NotificationRepository;
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
class FullFlowIntegrationTest {

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private CaseService caseService;

    @Test
    @WithMockUser(username = "admin", roles = "SUPERVISOR")
    void fullFlowFromAlertToDecision() {
        Party party = Party.builder()
                .type("PF")
                .document("60606060606")
                .name("Full Flow Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("HIGH")
                .status("OPEN")
                .title("Full Flow Alert")
                .ruleCode("PEP_MATCH")
                .createdAt(Instant.now())
                .build();
        alert = alertRepository.save(alert);

        var caseResponse = caseService.createFromAlert(alert.getId());
        assertEquals("OPEN", caseResponse.getStatus());
        assertEquals(alert.getId(), caseResponse.getAlertId());

        CaseDecisionRequest decision = new CaseDecisionRequest();
        decision.setDecision("ARCHIVE");
        decision.setJustification("Análise completa sem indícios");

        var decided = caseService.decide(caseResponse.getId(), decision);
        assertEquals("ARCHIVED", decided.getStatus());
        assertEquals("ARCHIVE", decided.getDecision());
        assertNotNull(decided.getDecidedAt());
    }
}
