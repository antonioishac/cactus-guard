package br.com.cactus.guard.migration;

import br.com.cactus.guard.migration.entity.MigrationBatch;
import br.com.cactus.guard.migration.repository.MigrationBatchRepository;
import br.com.cactus.guard.migration.service.MigrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MigrationBatchCreateTest {

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private MigrationBatchRepository batchRepository;

    @Test
    void createBatchShouldPersist() {
        var batch = migrationService.createBatch("CREATE_TEST");
        assertNotNull(batch.getId());
        assertEquals("PENDING", batch.getStatus());
        assertEquals(0, batch.getTotalRecords());

        MigrationBatch persisted = batchRepository.findById(batch.getId()).orElseThrow();
        assertEquals("CREATE_TEST", persisted.getName());
    }
}
