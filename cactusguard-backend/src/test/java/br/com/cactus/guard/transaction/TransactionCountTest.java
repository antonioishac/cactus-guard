package br.com.cactus.guard.transaction;

import br.com.cactus.guard.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TransactionCountTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void countTransactionsShouldBeNonNegative() {
        long count = transactionRepository.count();
        assertTrue(count >= 0);
    }
}
