package br.com.cactus.guard.report;

import br.com.cactus.guard.report.dto.ReportGenerateRequest;
import br.com.cactus.guard.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReportIntegrationTest {

    @Autowired
    private ReportService reportService;

    @Test
    @WithMockUser(username = "admin") // o job de relatorio registra o usuario solicitante
    void generateReportShouldCreateJob() {
        ReportGenerateRequest request = new ReportGenerateRequest();
        request.setFormat("PDF");
        request.setParameters(Map.of("period", "2026-01"));

        var job = reportService.generate("ALERTS_SUMMARY", request);

        assertNotNull(job.getId());
        assertEquals("ALERTS_SUMMARY", job.getReportCode());
        assertEquals("PDF", job.getFormat());
    }

    @Test
    void definitionsShouldExist() {
        var definitions = reportService.findAllDefinitions();
        assertFalse(definitions.isEmpty());
    }
}
