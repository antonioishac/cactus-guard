package br.com.cactus.guard.user;

import br.com.cactus.guard.user.dto.UserCreateRequest;
import br.com.cactus.guard.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserActiveDefaultTest {

    @Autowired
    private UserService userService;

    @Test
    void newUserShouldBeActiveByDefault() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("active_default");
        request.setEmail("activedefault@email.com");
        request.setPassword("senha123");
        request.setName("Active Default");

        var created = userService.create(request);
        assertTrue(created.isActive());
    }
}
