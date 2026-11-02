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
class UserRolesTest {

    @Autowired
    private UserService userService;

    @Test
    void createUserWithMultipleRoles() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("multi_role");
        request.setEmail("multi@cactusguard.com");
        request.setPassword("senha123");
        request.setName("Multi Role User");
        request.setRoles(Set.of("ANALYST", "SUPERVISOR"));

        var response = userService.create(request);

        assertEquals(2, response.getRoles().size());
        assertTrue(response.getRoles().contains("ANALYST"));
        assertTrue(response.getRoles().contains("SUPERVISOR"));
    }

    @Test
    void deactivateUserShouldWork() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("to_deactivate");
        request.setEmail("deactivate@cactusguard.com");
        request.setPassword("senha123");
        request.setName("To Deactivate");

        var created = userService.create(request);
        assertTrue(created.isActive());

        var deactivated = userService.deactivate(created.getId());
        assertFalse(deactivated.isActive());
    }
}
