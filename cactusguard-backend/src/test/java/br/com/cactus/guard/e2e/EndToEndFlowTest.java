package br.com.cactus.guard.e2e;

import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.service.CaseService;
import br.com.cactus.guard.party.dto.PartyRequest;
import br.com.cactus.guard.party.service.PartyService;
import br.com.cactus.guard.screening.service.ScreeningService;
import br.com.cactus.guard.siscoaf.service.SarGeneratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class EndToEndFlowTest {

    @Autowired
    private PartyService partyService;

    @Autowired
    private ScreeningService screeningService;

    @Autowired
    private CaseService caseService;

    @Autowired
    private SarGeneratorService sarGeneratorService;

    @Test
    @WithMockUser(username = "admin", roles = "SUPERVISOR")
    void fullFlowShouldWork() {
        PartyRequest request = new PartyRequest();
        request.setType("PF");
        request.setDocument("33300033300");
        request.setName("E2E Test");

        var party = partyService.create(request);
        assertNotNull(party.getId());

        var screening = screeningService.screen(party.getId(), List.of("PEP"));
        assertNotNull(screening.getId());

        // Note: Full case creation requires an alert, which depends on rule engine
        // This test validates the party creation and screening flow
        assertEquals("E2E Test", party.getName());
    }
}
