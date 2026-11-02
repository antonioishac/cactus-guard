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
class SiscoafFindByIdTest {

    @Autowired
    private SiscoafService siscoafService;

    @Autowired
    private CaseService caseService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Test
    void findByIdShouldReturnCommunication() {
        Party party = Party.builder()
                .type("PF")
                .document("48484848484")
                .name("Find By Id SISCOAF")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("HIGH")
                .status("OPEN")
                .title("Find Id Alert")
                .ruleCode("PEP_MATCH")
                .createdAt(Instant.now())
                .build();
        alert = alertRepository.save(alert);

        var caseResp = caseService.createFromAlert(alert.getId());
        var draft = siscoafService.createDraft(caseResp.getId(), "COE", Map.of());

        var found = siscoafService.findById(draft.getId());
        assertEquals(draft.getId(), found.getId());
        assertEquals("DRAFT", found.getStatus());
    }
}
