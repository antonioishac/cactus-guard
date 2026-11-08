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
class MigrationWithRiskTest {

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void migratedPartyShouldHavePendingRisk() {
        var batch = migrationService.createBatch("RISK_TEST");
        migrationService.addRecord(batch.getId(), "RISK-001", "40404040404",
                Map.of("name", "Risk Migrated", "type", "PF"));

        migrationService.processBatch(batch.getId());

        var party = partyRepository.findByDocumentHash(DocumentHash.of("40404040404"));
        assertTrue(party.isPresent());
        assertEquals("PENDING", party.get().getRiskLevel());
    }
}
