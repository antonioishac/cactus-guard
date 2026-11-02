package br.com.cactus.guard.user;

import br.com.cactus.guard.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserCountTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void countUsersShouldBeAtLeastOne() {
        long count = userRepository.count();
        assertTrue(count >= 1);
    }
}
