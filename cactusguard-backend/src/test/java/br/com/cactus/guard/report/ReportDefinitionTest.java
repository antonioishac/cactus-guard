package br.com.cactus.guard.report;

import br.com.cactus.guard.report.repository.ReportDefinitionRepository;
import br.com.cactus.guard.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReportDefinitionTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportDefinitionRepository definitionRepository;

    @Test
    void allDefinitionsShouldBeActive() {
        var definitions = reportService.findAllDefinitions();
        assertFalse(definitions.isEmpty());
        assertTrue(definitions.stream().allMatch(d -> d.isActive()));
    }

    @Test
    void alertsSummaryShouldExist() {
        var def = definitionRepository.findByCode("ALERTS_SUMMARY");
        assertTrue(def.isPresent());
        assertEquals("Resumo de Alertas", def.get().getName());
    }
}
