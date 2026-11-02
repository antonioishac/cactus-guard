package br.com.cactus.guard.user;

import br.com.cactus.guard.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserFindAllTest {

    @Autowired
    private UserService userService;

    @Test
    void findAllShouldReturnUsers() {
        var page = userService.findAll(org.springframework.data.domain.Pageable.unpaged());
        assertFalse(page.isEmpty());
    }
}
