package br.com.cactus.guard.siscoaf;

import br.com.cactus.guard.siscoaf.repository.SiscoafCommunicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SiscoafCountTest {

    @Autowired
    private SiscoafCommunicationRepository repository;

    @Test
    void countCommunicationsShouldBeNonNegative() {
        long count = repository.count();
        assertTrue(count >= 0);
    }
}
