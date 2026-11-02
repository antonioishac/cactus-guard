package br.com.cactus.guard.audit;

import br.com.cactus.guard.audit.repository.AuditLogRepository;
import br.com.cactus.guard.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuditQueryTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void queryByEntityShouldReturnResults() {
        UUID entityId = UUID.randomUUID();
        auditService.log("QUERY_TEST", "PARTY", entityId, null, Map.of("test", true));

        var page = auditService.findByEntity("PARTY", entityId, org.springframework.data.domain.Pageable.unpaged());
        assertFalse(page.isEmpty());
    }
}
