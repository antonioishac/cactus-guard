package br.com.cactus.guard.case_;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.service.CaseService;
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
class CaseEscalateTest {

    @Autowired
    private CaseService caseService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Test
    @WithMockUser(username = "admin", roles = "SUPERVISOR")
    void escalateCaseShouldUpdateStatus() {
        Party party = Party.builder()
                .type("PF")
                .document("27272727272")
                .name("Escalate Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("CRITICAL")
                .status("OPEN")
                .title("Escalate Alert")
                .ruleCode("SANCTION_MATCH")
                .createdAt(Instant.now())
                .build();
        alert = alertRepository.save(alert);

        var caseResp = caseService.createFromAlert(alert.getId());

        CaseDecisionRequest decision = new CaseDecisionRequest();
        decision.setDecision("ESCALATED");
        decision.setJustification("Requer análise superior");

        var decided = caseService.decide(caseResp.getId(), decision);
        assertEquals("ESCALATED", decided.getStatus());
    }
}
