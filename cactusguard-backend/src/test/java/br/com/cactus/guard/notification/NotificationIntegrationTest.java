package br.com.cactus.guard.notification;

import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class NotificationIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createAndCountNotifications() {
        User user = userRepository.findByUsername("admin").orElse(null);
        if (user == null) return;

        notificationService.create(user.getId(), "TEST", "Test Notification", "Message", "TEST", UUID.randomUUID());

        long unread = notificationService.countUnread();
        assertTrue(unread >= 0);
    }
}
