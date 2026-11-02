package br.com.cactus.guard.siscoaf;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.case_.service.CaseService;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.siscoaf.service.SiscoafService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SiscoafTypeTest {

    @Autowired
    private SiscoafService siscoafService;

    @Autowired
    private CaseService caseService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Test
    void communicationTypeShouldBePersisted() {
        Party party = Party.builder()
                .type("PF")
                .document("60606060606")
                .name("Type SISCOAF")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("CRITICAL")
                .status("OPEN")
                .title("Type Alert")
                .ruleCode("SANCTION_MATCH")
                .createdAt(Instant.now())
                .build();
        alert = alertRepository.save(alert);

        var caseResp = caseService.createFromAlert(alert.getId());
        var draft = siscoafService.createDraft(caseResp.getId(), "COS", Map.of("value", 100000));

        assertEquals("COS", draft.getType());
    }
}
