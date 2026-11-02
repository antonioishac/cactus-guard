package br.com.cactus.guard.screening;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.screening.service.ScreeningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ScreeningAsyncTest {

    @Autowired
    private ScreeningService screeningService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void screenAsyncShouldReturnFuture() {
        Party party = Party.builder()
                .type("PF")
                .document("22200022200")
                .documentHash("hash222")
                .name("Async Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        CompletableFuture<?> future = screeningService.screenAsync(party.getId(), List.of("PEP"));
        assertNotNull(future);
    }
}
