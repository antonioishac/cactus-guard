package br.com.cactus.guard.report;

import br.com.cactus.guard.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReportFindMyJobsTest {

    @Autowired
    private ReportService reportService;

    @Test
    void findMyJobsShouldReturnPage() {
        var request = new br.com.cactus.guard.report.dto.ReportGenerateRequest();
        request.setFormat("EXCEL");

        reportService.generate("SCREENING_PENDING", request);

        var page = reportService.findMyJobs(org.springframework.data.domain.Pageable.unpaged());
        assertNotNull(page);
    }
}
