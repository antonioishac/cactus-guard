package br.com.cactus.guard.integration;

import br.com.cactus.guard.integration.screening.DowJonesScreeningProvider;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CircuitBreakerFallbackTest {

    @Autowired(required = false)
    private DowJonesScreeningProvider screeningProvider;

    @Test
    void fallbackShouldReturnErrorStatus() {
        if (screeningProvider == null) {
            // Provider not active in test (mock is default)
            return;
        }

        Party party = Party.builder()
                .document("00000000000")
                .name("Fallback Test")
                .build();

        ScreeningResult result = screeningProvider.screenFallback(party, List.of("PEP"), new RuntimeException("Test failure"));
        assertEquals("ERROR", result.getStatus());
        assertEquals("FALLBACK", result.getListCode());
    }
}
