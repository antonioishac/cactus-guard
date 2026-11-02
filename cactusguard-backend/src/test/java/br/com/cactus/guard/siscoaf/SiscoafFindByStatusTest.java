package br.com.cactus.guard.siscoaf;

import br.com.cactus.guard.siscoaf.repository.SiscoafCommunicationRepository;
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
class SiscoafFindByStatusTest {

    @Autowired
    private SiscoafService siscoafService;

    @Autowired
    private SiscoafCommunicationRepository repository;

    @Autowired
    private CaseService caseService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Test
    void findByStatusShouldReturnResults() {
        Party party = Party.builder()
                .type("PF")
                .document("30303030303")
                .name("Find Status Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("HIGH")
                .status("OPEN")
                .title("Status Find")
                .ruleCode("PEP_MATCH")
                .createdAt(Instant.now())
                .build();
        alert = alertRepository.save(alert);

        var caseResp = caseService.createFromAlert(alert.getId());
        siscoafService.createDraft(caseResp.getId(), "COE", Map.of());

        var results = repository.findByStatus("DRAFT", org.springframework.data.domain.Pageable.unpaged());
        assertFalse(results.isEmpty());
    }
}
