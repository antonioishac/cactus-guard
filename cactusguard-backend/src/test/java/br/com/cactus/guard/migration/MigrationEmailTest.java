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
class MigrationEmailTest {

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void migratedPartyShouldHaveEmail() {
        var batch = migrationService.createBatch("EMAIL_TEST");
        migrationService.addRecord(batch.getId(), "EM-001", "61616161616",
                Map.of("name", "Email Test", "type", "PF", "email", "test@email.com"));

        migrationService.processBatch(batch.getId());

        var party = partyRepository.findByDocumentHash(DocumentHash.of("61616161616"));
        assertTrue(party.isPresent());
        assertEquals("test@email.com", party.get().getEmail());
    }
}
