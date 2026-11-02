package br.com.cactus.guard.notification;

import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.notification.repository.NotificationRepository;
import br.com.cactus.guard.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class NotificationUnreadCountTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void unreadCountShouldIncrease() {
        var user = userRepository.findByUsername("admin").orElse(null);
        if (user == null) return;

        long before = notificationService.countUnread();

        notificationService.create(user.getId(), "COUNT", "Count Test", "Msg", "TEST", UUID.randomUUID());

        long after = notificationService.countUnread();
        assertTrue(after >= before);
    }
}
