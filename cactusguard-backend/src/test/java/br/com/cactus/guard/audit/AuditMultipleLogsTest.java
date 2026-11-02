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
class AuditMultipleLogsTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void multipleLogsShouldBeQueryable() {
        UUID entityId = UUID.randomUUID();

        auditService.log("ACTION_1", "PARTY", entityId, null, Map.of("step", 1));
        auditService.log("ACTION_2", "PARTY", entityId, null, Map.of("step", 2));
        auditService.log("ACTION_3", "PARTY", entityId, null, Map.of("step", 3));

        var page = auditService.findByEntity("PARTY", entityId, org.springframework.data.domain.Pageable.unpaged());
        assertEquals(3, page.getTotalElements());
    }
}
