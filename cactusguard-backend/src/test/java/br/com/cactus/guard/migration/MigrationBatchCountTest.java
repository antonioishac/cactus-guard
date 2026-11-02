package br.com.cactus.guard.migration;

import br.com.cactus.guard.migration.repository.MigrationBatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MigrationBatchCountTest {

    @Autowired
    private MigrationBatchRepository batchRepository;

    @Test
    void countBatchesShouldBeNonNegative() {
        long count = batchRepository.count();
        assertTrue(count >= 0);
    }
}
