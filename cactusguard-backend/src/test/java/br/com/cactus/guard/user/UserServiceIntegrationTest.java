package br.com.cactus.guard.user;

import br.com.cactus.guard.user.dto.UserCreateRequest;
import br.com.cactus.guard.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Test
    void createUserShouldWork() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("analyst1");
        request.setEmail("analyst1@cactusguard.com");
        request.setPassword("senha123");
        request.setName("Analista Um");
        request.setRoles(Set.of("ANALYST"));

        var response = userService.create(request);

        assertNotNull(response.getId());
        assertEquals("analyst1", response.getUsername());
        assertTrue(response.isActive());
        assertTrue(response.getRoles().contains("ANALYST"));
    }
}
