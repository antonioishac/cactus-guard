package br.com.cactus.guard.alert;

import br.com.cactus.guard.alert.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AlertCountAllTest {

    @Autowired
    private AlertRepository alertRepository;

    @Test
    void countAllAlertsShouldBeNonNegative() {
        long count = alertRepository.count();
        assertTrue(count >= 0);
    }
}
