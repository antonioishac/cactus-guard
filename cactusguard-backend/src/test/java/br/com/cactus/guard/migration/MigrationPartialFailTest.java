package br.com.cactus.guard.migration;

import br.com.cactus.guard.common.util.DocumentHash;
import br.com.cactus.guard.migration.service.MigrationService;
import br.com.cactus.guard.party.repository.PartyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MigrationPartialFailTest {

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void partialFailShouldMarkBatchAsPartial() {
        var batch = migrationService.createBatch("PARTIAL_TEST");

        migrationService.addRecord(batch.getId(), "PF-001", "55555555555",
                Map.of("name", "Valid", "type", "PF"));
        migrationService.addRecord(batch.getId(), "PF-002", null,
                Map.of("name", "Invalid"));

        migrationService.processBatch(batch.getId());

        var updated = partyRepository.findByDocumentHash(DocumentHash.of("55555555555"));
        assertTrue(updated.isPresent());
    }
}
