package br.com.cactus.guard.case_;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.service.CaseService;
import br.com.cactus.guard.common.exception.BusinessException;
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
class CaseReopenPreventionTest {

    @Autowired
    private CaseService caseService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Test
    @WithMockUser(username = "admin", roles = "SUPERVISOR")
    void shouldPreventReopeningDecidedCase() {
        Party party = Party.builder()
                .type("PF")
                .document("11122233344")
                .name("Reopen Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("HIGH")
                .status("OPEN")
                .title("Reopen Alert")
                .ruleCode("PEP_MATCH")
                .createdAt(Instant.now())
                .build();
        alert = alertRepository.save(alert);

        var caseResp = caseService.createFromAlert(alert.getId());

        CaseDecisionRequest decision = new CaseDecisionRequest();
        decision.setDecision("APPROVED");
        decision.setJustification("Primeira decisão");
        caseService.decide(caseResp.getId(), decision);

        CaseDecisionRequest second = new CaseDecisionRequest();
        second.setDecision("REJECTED");
        second.setJustification("Tentativa de reabertura");

        assertThrows(BusinessException.class, () -> caseService.decide(caseResp.getId(), second));
    }
}
