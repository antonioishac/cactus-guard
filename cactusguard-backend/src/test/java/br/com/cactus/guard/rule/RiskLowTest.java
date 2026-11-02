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
class RiskLowTest {

    @Autowired
    private RiskScoringService riskScoringService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void partyWithoutMatchesShouldBeLowRisk() {
        Party party = Party.builder()
                .type("PF")
                .document("19191919191")
                .name("Low Risk Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        String risk = riskScoringService.calculateRisk(party);
        assertEquals("LOW", risk);
    }
}
