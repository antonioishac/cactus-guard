package br.com.cactus.guard.screening;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.screening.entity.ScreeningList;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import br.com.cactus.guard.screening.repository.ScreeningListRepository;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import br.com.cactus.guard.screening.service.ScreeningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ScreeningIntegrationTest {

    @Autowired
    private ScreeningService screeningService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private ScreeningResultRepository resultRepository;

    @Test
    void reviewScreeningResultShouldUpdateStatus() {
        Party party = Party.builder()
                .type("PF")
                .document("10101010101")
                .name("Screening Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        ScreeningResult result = ScreeningResult.builder()
                .party(party)
                .listCode("PEP_BR")
                .matchScore(new BigDecimal("95.5"))
                .status("PENDING")
                .build();
        result = resultRepository.save(result);

        var response = screeningService.review(result.getId(), "CONFIRMED");
        assertEquals("CONFIRMED", response.getStatus());
    }
}
