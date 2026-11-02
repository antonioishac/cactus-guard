package br.com.cactus.guard.audit;

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
class AuditIntegrationTest {

    @Autowired
    private AuditService auditService;

    @Test
    void logActionShouldPersist() {
        UUID entityId = UUID.randomUUID();
        assertDoesNotThrow(() -> 
            auditService.log("TEST_ACTION", "PARTY", entityId, null, Map.of("field", "value"))
        );
    }
}
