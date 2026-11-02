package br.com.cactus.guard.case_;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.case_.service.CaseService;
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
class CaseCountTest {

    @Autowired
    private CaseService caseService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Test
    void countOpenCasesShouldWork() {
        Party party = Party.builder()
                .type("PF")
                .document("45454545454")
                .name("Count Case")
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
        alert = alertRepository.save(alert);

        caseService.createFromAlert(alert.getId());

        long open = caseService.countOpen();
        assertTrue(open >= 1);
    }
}
