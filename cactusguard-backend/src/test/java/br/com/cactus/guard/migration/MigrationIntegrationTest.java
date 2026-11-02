package br.com.cactus.guard.migration;

import br.com.cactus.guard.migration.entity.MigrationBatch;
import br.com.cactus.guard.migration.service.MigrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MigrationIntegrationTest {

    @Autowired
    private MigrationService migrationService;

    @Test
    void processBatchShouldCreateParties() {
        MigrationBatch batch = migrationService.createBatch("LEGACY_SYSTEM");

        migrationService.addRecord(batch.getId(), "EXT-001", "11122233344",
                Map.of("name", "Maria Migrada", "type", "PF", "email", "maria@email.com"));

        migrationService.addRecord(batch.getId(), "EXT-002", "55566677788",
                Map.of("name", "Empresa Migrada", "type", "PJ"));

        int processed = migrationService.processBatch(batch.getId());

        assertEquals(2, processed);
    }
}
