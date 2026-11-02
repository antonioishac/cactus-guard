package br.com.cactus.guard.auth;

import br.com.cactus.guard.auth.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RoleSeedTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void seedRolesShouldExist() {
        assertTrue(roleRepository.findByName("ADMIN").isPresent());
        assertTrue(roleRepository.findByName("ANALYST").isPresent());
        assertTrue(roleRepository.findByName("SUPERVISOR").isPresent());
    }
}
