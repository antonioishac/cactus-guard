package br.com.cactus.guard.screening;

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
class ScreeningRiskTest {

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private ScreeningResultRepository resultRepository;

    @Autowired
    private RiskScoringService riskScoringService;

    @Test
    void confirmedPepShouldResultInHighRisk() {
        Party party = Party.builder()
                .type("PF")
                .document("80808080808")
                .name("PEP Risk Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        ScreeningResult result = ScreeningResult.builder()
                .party(party)
                .listCode("PEP_BR")
                .matchScore(new BigDecimal("98.0"))
                .status("CONFIRMED")
                .build();
        resultRepository.save(result);

        String risk = riskScoringService.calculateRisk(party);
        assertEquals("HIGH", risk);
    }

    @Test
    void confirmedSanctionShouldResultInCriticalRisk() {
        Party party = Party.builder()
                .type("PJ")
                .document("90909090909")
                .name("Sanction Risk Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        ScreeningResult result = ScreeningResult.builder()
                .party(party)
                .listCode("OFAC")
                .matchScore(new BigDecimal("100.0"))
                .status("CONFIRMED")
                .build();
        resultRepository.save(result);

        String risk = riskScoringService.calculateRisk(party);
        assertEquals("CRITICAL", risk);
    }
}
