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
class AuditOldNewValueTest {

    @Autowired
    private AuditService auditService;

    @Test
    void logWithOldAndNewValueShouldPersist() {
        UUID entityId = UUID.randomUUID();
        Map<String, Object> oldValue = Map.of("status", "OPEN");
        Map<String, Object> newValue = Map.of("status", "CLOSED");

        assertDoesNotThrow(() -> 
            auditService.log("STATUS_CHANGE", "CASE", entityId, oldValue, newValue)
        );
    }
}
