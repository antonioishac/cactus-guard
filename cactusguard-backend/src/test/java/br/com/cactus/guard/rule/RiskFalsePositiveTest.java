package br.com.cactus.guard.rule;

import br.com.cactus.guard.rule.engine.RiskScoringService;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
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
class RiskFalsePositiveTest {

    @Autowired
    private RiskScoringService riskScoringService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private ScreeningResultRepository resultRepository;

    @Test
    void falsePositiveShouldNotIncreaseRisk() {
        Party party = Party.builder()
                .type("PF")
                .document("62626262626")
                .name("False Positive Risk")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        ScreeningResult result = ScreeningResult.builder()
                .party(party)
                .listCode("PEP_BR")
                .matchScore(new BigDecimal("90.0"))
                .status("FALSE_POSITIVE")
                .build();
        resultRepository.save(result);

        String risk = riskScoringService.calculateRisk(party);
        assertEquals("LOW", risk);
    }
}
