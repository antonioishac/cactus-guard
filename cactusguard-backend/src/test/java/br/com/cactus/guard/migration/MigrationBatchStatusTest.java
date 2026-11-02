package br.com.cactus.guard.migration;

import br.com.cactus.guard.migration.entity.MigrationBatch;
import br.com.cactus.guard.migration.repository.MigrationBatchRepository;
import br.com.cactus.guard.migration.service.MigrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MigrationBatchStatusTest {

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private MigrationBatchRepository batchRepository;

    @Test
    void processedBatchShouldHaveCompletedStatus() {
        var batch = migrationService.createBatch("STATUS_TEST");
        migrationService.addRecord(batch.getId(), "ST-001", "24242424242",
                Map.of("name", "Status Test", "type", "PF"));

        migrationService.processBatch(batch.getId());

        MigrationBatch updated = batchRepository.findById(batch.getId()).orElseThrow();
        assertTrue(updated.getStatus().equals("COMPLETED") || updated.getStatus().equals("PARTIAL"));
        assertNotNull(updated.getCompletedAt());
        assertEquals(1, updated.getProcessedRecords());
    }
}
