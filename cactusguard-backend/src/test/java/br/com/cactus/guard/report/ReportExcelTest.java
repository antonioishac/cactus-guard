package br.com.cactus.guard.report;

import br.com.cactus.guard.report.dto.ReportGenerateRequest;
import br.com.cactus.guard.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReportExcelTest {

    @Autowired
    private ReportService reportService;

    @Test
    void generateExcelReportShouldCreateJob() {
        ReportGenerateRequest request = new ReportGenerateRequest();
        request.setFormat("EXCEL");
        request.setParameters(Map.of("period", "2026-Q1"));

        var job = reportService.generate("ALERTS_SUMMARY", request);

        assertNotNull(job.getId());
        assertEquals("EXCEL", job.getFormat());
        assertEquals("PENDING", job.getStatus());
    }
}
