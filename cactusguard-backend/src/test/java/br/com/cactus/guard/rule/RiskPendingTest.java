package br.com.cactus.guard.rule;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.engine.RiskScoringService;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RiskPendingTest {

    @Autowired
    private RiskScoringService riskScoringService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private ScreeningResultRepository resultRepository;

    @Test
    void pendingMatchShouldResultInMediumRisk() {
        Party party = Party.builder()
                .type("PF")
                .document("38383838383")
                .name("Pending Risk")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        ScreeningResult result = ScreeningResult.builder()
                .party(party)
                .listCode("PEP_BR")
                .matchScore(new BigDecimal("60.0"))
                .status("PENDING")
                .build();
        resultRepository.save(result);

        String risk = riskScoringService.calculateRisk(party);
        assertEquals("MEDIUM", risk);
    }
}
