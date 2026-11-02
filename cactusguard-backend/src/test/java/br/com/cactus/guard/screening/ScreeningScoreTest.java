package br.com.cactus.guard.screening;

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
class ScreeningScoreTest {

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private ScreeningResultRepository resultRepository;

    @Test
    void matchScoreShouldBeSaved() {
        Party party = Party.builder()
                .type("PF")
                .document("67676767676")
                .name("Score Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        ScreeningResult result = ScreeningResult.builder()
                .party(party)
                .listCode("PEP_BR")
                .matchScore(new BigDecimal("87.5"))
                .status("PENDING")
                .build();
        result = resultRepository.save(result);

        assertEquals(new BigDecimal("87.5"), result.getMatchScore());
    }
}
