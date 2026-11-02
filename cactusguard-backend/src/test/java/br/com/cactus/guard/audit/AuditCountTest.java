package br.com.cactus.guard.audit;

import br.com.cactus.guard.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuditCountTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void countAuditLogsShouldBeNonNegative() {
        long count = auditLogRepository.count();
        assertTrue(count >= 0);
    }
}
