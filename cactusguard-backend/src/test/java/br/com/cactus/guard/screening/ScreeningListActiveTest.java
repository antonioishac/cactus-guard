package br.com.cactus.guard.screening;

import br.com.cactus.guard.screening.service.ScreeningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ScreeningListActiveTest {

    @Autowired
    private ScreeningService screeningService;

    @Test
    void findAllListsShouldReturnActive() {
        var lists = screeningService.findAllLists();
        assertFalse(lists.isEmpty());
        assertTrue(lists.stream().allMatch(l -> l.isActive()));
    }
}
