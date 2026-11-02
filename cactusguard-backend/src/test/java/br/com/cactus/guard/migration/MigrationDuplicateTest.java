package br.com.cactus.guard.migration;

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
class MigrationDuplicateTest {

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void duplicateDocumentShouldNotCreateNewParty() {
        var batch = migrationService.createBatch("DUP_TEST");

        migrationService.addRecord(batch.getId(), "DUP-001", "15151515151",
                Map.of("name", "First", "type", "PF"));
        migrationService.processBatch(batch.getId());

        long countBefore = partyRepository.count();

        var batch2 = migrationService.createBatch("DUP_TEST_2");
        migrationService.addRecord(batch2.getId(), "DUP-002", "15151515151",
                Map.of("name", "Second", "type", "PF"));
        migrationService.processBatch(batch2.getId());

        long countAfter = partyRepository.count();
        assertEquals(countBefore, countAfter);
    }
}
