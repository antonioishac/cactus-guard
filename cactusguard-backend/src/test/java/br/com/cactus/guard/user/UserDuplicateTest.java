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
class UserDuplicateTest {

    @Autowired
    private UserService userService;

    @Test
    void duplicateUsernameShouldFail() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("dup_user");
        request.setEmail("dup1@email.com");
        request.setPassword("senha123");
        request.setName("Dup User");

        userService.create(request);

        UserCreateRequest duplicate = new UserCreateRequest();
        duplicate.setUsername("dup_user");
        duplicate.setEmail("dup2@email.com");
        duplicate.setPassword("senha123");
        duplicate.setName("Dup User 2");

        assertThrows(IllegalArgumentException.class, () -> userService.create(duplicate));
    }
}
