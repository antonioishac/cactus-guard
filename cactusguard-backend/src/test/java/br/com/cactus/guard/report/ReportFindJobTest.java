package br.com.cactus.guard.report;

import br.com.cactus.guard.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReportFindJobTest {

    @Autowired
    private ReportService reportService;

    @Test
    @WithMockUser(username = "admin") // o job de relatorio registra o usuario solicitante
    void findJobShouldReturnJob() {
        var request = new br.com.cactus.guard.report.dto.ReportGenerateRequest();
        request.setFormat("PDF");

        var job = reportService.generate("PEP_EXPOSURE", request);
        var found = reportService.findJob(job.getId());

        assertEquals(job.getId(), found.getId());
        assertEquals("PEP_EXPOSURE", found.getReportCode());
    }
}
