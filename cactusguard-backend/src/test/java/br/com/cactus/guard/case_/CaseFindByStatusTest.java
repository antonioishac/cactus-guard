package br.com.cactus.guard.case_;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.case_.repository.CaseRepository;
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
class CaseFindByStatusTest {

    @Autowired
    private CaseService caseService;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Test
    void findByStatusShouldReturnCases() {
        Party party = Party.builder()
                .type("PF")
                .document("33333333333")
                .name("Find Status Case")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("MEDIUM")
                .status("OPEN")
                .title("Status Case")
                .ruleCode("PEP_MATCH")
                .createdAt(Instant.now())
                .build();
        alert = alertRepository.save(alert);

        caseService.createFromAlert(alert.getId());

        var cases = caseRepository.findByStatus("OPEN", org.springframework.data.domain.Pageable.unpaged());
        assertFalse(cases.isEmpty());
    }
}
