package br.com.cactus.guard.screening;

import br.com.cactus.guard.screening.repository.ScreeningListRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ScreeningListCountTest {

    @Autowired
    private ScreeningListRepository listRepository;

    @Test
    void countListsShouldBeAtLeastThree() {
        long count = listRepository.count();
        assertTrue(count >= 3);
    }
}
