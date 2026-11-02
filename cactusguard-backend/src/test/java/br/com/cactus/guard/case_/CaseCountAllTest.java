package br.com.cactus.guard.case_;

import br.com.cactus.guard.case_.repository.CaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CaseCountAllTest {

    @Autowired
    private CaseRepository caseRepository;

    @Test
    void countAllCasesShouldBeNonNegative() {
        long count = caseRepository.count();
        assertTrue(count >= 0);
    }
}
