package br.com.cactus.guard.party;

import br.com.cactus.guard.party.repository.PartyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PartyCountTest {

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void countPartiesShouldBeNonNegative() {
        long count = partyRepository.count();
        assertTrue(count >= 0);
    }
}
