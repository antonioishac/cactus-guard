package br.com.cactus.guard.screening;

import br.com.cactus.guard.screening.repository.ScreeningListRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ScreeningListSeedTest {

    @Autowired
    private ScreeningListRepository listRepository;

    @Test
    void seedListsShouldExist() {
        assertTrue(listRepository.findByCode("PEP_BR").isPresent());
        assertTrue(listRepository.findByCode("UN_SANCTIONS").isPresent());
        assertTrue(listRepository.findByCode("OFAC").isPresent());
    }

    @Test
    void allListsShouldBeActive() {
        var lists = listRepository.findAll();
        assertTrue(lists.stream().allMatch(l -> l.isActive()));
    }
}
