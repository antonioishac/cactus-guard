package br.com.cactus.guard.rule;

import br.com.cactus.guard.rule.engine.RiskScoringService;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RiskRecalculateTest {

    @Autowired
    private RiskScoringService riskScoringService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void recalculateAllShouldUpdateRiskLevels() {
        Party party = Party.builder()
                .type("PF")
                .document("54545454545")
                .name("Recalculate Test")
                .status("ACTIVE")
                .riskLevel("PENDING")
                .build();
        party = partyRepository.save(party);

        assertDoesNotThrow(() -> riskScoringService.recalculateAllRiskScores());

        var updated = partyRepository.findById(party.getId());
        assertTrue(updated.isPresent());
        assertNotEquals("PENDING", updated.get().getRiskLevel());
    }
}
