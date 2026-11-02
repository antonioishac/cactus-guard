package br.com.cactus.guard.report;

import br.com.cactus.guard.report.repository.ReportJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReportJobCountTest {

    @Autowired
    private ReportJobRepository jobRepository;

    @Test
    void countJobsShouldBeNonNegative() {
        long count = jobRepository.count();
        assertTrue(count >= 0);
    }
}
