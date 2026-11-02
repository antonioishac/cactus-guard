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
class UserFindByIdTest {

    @Autowired
    private UserService userService;

    @Test
    void findByIdShouldReturnUser() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("find_by_id");
        request.setEmail("findbyid@email.com");
        request.setPassword("senha123");
        request.setName("Find By Id");

        var created = userService.create(request);
        var found = userService.findById(created.getId());

        assertEquals(created.getId(), found.getId());
        assertEquals("find_by_id", found.getUsername());
    }
}
