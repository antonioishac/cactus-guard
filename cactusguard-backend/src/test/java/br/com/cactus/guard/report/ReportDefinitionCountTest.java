package br.com.cactus.guard.report;

import br.com.cactus.guard.report.repository.ReportDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReportDefinitionCountTest {

    @Autowired
    private ReportDefinitionRepository definitionRepository;

    @Test
    void countDefinitionsShouldBeAtLeastFive() {
        long count = definitionRepository.count();
        assertTrue(count >= 5);
    }
}
