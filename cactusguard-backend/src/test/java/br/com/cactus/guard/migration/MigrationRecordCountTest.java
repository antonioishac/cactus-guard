package br.com.cactus.guard.migration;

import br.com.cactus.guard.migration.repository.MigrationRecordRepository;
import br.com.cactus.guard.migration.service.MigrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MigrationRecordCountTest {

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private MigrationRecordRepository recordRepository;

    @Test
    void processedRecordsShouldBeCountable() {
        var batch = migrationService.createBatch("COUNT_TEST");
        migrationService.addRecord(batch.getId(), "CT-001", "36363636363",
                Map.of("name", "Count Test", "type", "PF"));
        migrationService.addRecord(batch.getId(), "CT-002", "37373737373",
                Map.of("name", "Count Test 2", "type", "PJ"));

        migrationService.processBatch(batch.getId());

        long processed = recordRepository.countByBatchIdAndStatus(batch.getId(), "PROCESSED");
        assertEquals(2, processed);
    }
}
