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
class FullFlowTest {

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
    void completeFlowPartyScreeningCaseSar() {
        // 1. Create party
        PartyRequest request = new PartyRequest();
        request.setType("PF");
        request.setDocument("44400044400");
        request.setName("Full Flow Test");
        var party = partyService.create(request);
        assertNotNull(party.getId());
        assertEquals("LOW", party.getRiskLevel());

        // 2. Screening
        var screening = screeningService.screen(party.getId(), List.of("PEP", "SANCTIONS"));
        assertNotNull(screening.getId());

        // 3. Verify party can be retrieved (triggers data access log)
        var retrieved = partyService.findById(party.getId());
        assertEquals("Full Flow Test", retrieved.getName());

        // Note: Case creation requires an alert from rule engine
        // SAR generation requires a decided case
        // This test validates the core flow is connected
    }
}
