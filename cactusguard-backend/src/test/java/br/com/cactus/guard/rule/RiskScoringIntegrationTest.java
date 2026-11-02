package br.com.cactus.guard.rule;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.engine.RiskScoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RiskScoringIntegrationTest {

    @Autowired
    private RiskScoringService riskScoringService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void calculateRiskShouldReturnLevel() {
        Party party = Party.builder()
                .type("PF")
                .document("30303030303")
                .name("Risk Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        String risk = riskScoringService.calculateRisk(party);
        assertNotNull(risk);
        assertTrue(java.util.List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(risk));
    }

    @Test
    void recalculateAllShouldNotFail() {
        assertDoesNotThrow(() -> riskScoringService.recalculateAllRiskScores());
    }
}
