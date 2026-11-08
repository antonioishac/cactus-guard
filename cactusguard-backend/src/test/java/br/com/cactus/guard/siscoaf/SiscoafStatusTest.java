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
class SiscoafStatusTest {

    @Autowired
    private SiscoafService siscoafService;

    @Autowired
    private CaseService caseService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Test
    void submitShouldGenerateProtocol() {
        Party party = Party.builder()
                .type("PJ")
                .document("13131313131")
                .name("SISCOAF Status Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("TRANSACTIONAL")
                .severity("CRITICAL")
                .status("OPEN")
                .title("Status Test Alert")
                .ruleCode("STRUCTURING")
                .createdAt(Instant.now())
                .build();
        alert = alertRepository.save(alert);

        var caseResp = caseService.createFromAlert(alert.getId());
        var draft = siscoafService.createDraft(caseResp.getId(), "COE", Map.of("value", 50000));
        var submitted = siscoafService.submit(draft.getId());

        assertNotNull(submitted.getProtocolNumber());
        // protocolo vem do provider; no perfil test o MockSiscoafProvider gera "MOCK-<epoch>"
        assertTrue(submitted.getProtocolNumber().startsWith("MOCK-"));
        assertEquals("SUBMITTED", submitted.getStatus());
    }
}
