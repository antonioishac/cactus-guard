package br.com.cactus.guard.kyc;

import br.com.cactus.guard.kyc.repository.KycProcessRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class KycProcessCountTest {

    @Autowired
    private KycProcessRepository kycRepository;

    @Test
    void countProcessesShouldBeNonNegative() {
        long count = kycRepository.count();
        assertTrue(count >= 0);
    }
}
