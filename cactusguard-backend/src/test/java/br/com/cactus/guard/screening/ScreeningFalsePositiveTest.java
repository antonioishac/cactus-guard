package br.com.cactus.guard.screening;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import br.com.cactus.guard.screening.service.ScreeningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ScreeningFalsePositiveTest {

    @Autowired
    private ScreeningService screeningService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private ScreeningResultRepository resultRepository;

    @Test
    void markFalsePositiveShouldUpdateStatus() {
        Party party = Party.builder()
                .type("PF")
                .document("18181818181")
                .name("False Positive Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        ScreeningResult result = ScreeningResult.builder()
                .party(party)
                .listCode("PEP_BR")
                .matchScore(new BigDecimal("75.0"))
                .status("PENDING")
                .build();
        result = resultRepository.save(result);

        var response = screeningService.review(result.getId(), "FALSE_POSITIVE");
        assertEquals("FALSE_POSITIVE", response.getStatus());
    }
}
