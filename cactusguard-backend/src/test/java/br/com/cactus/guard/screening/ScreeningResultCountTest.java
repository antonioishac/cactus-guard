package br.com.cactus.guard.screening;

import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ScreeningResultCountTest {

    @Autowired
    private ScreeningResultRepository resultRepository;

    @Test
    void countResultsShouldBeNonNegative() {
        long count = resultRepository.count();
        assertTrue(count >= 0);
    }
}
