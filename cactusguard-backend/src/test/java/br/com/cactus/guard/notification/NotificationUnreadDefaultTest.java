package br.com.cactus.guard.notification;

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
class NotificationUnreadDefaultTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void newNotificationShouldBeUnread() {
        var user = userRepository.findByUsername("admin").orElse(null);
        if (user == null) return;

        var notification = notificationService.create(
                user.getId(), "UNREAD_DEFAULT", "Unread Test", "Msg", "TEST", UUID.randomUUID());

        assertFalse(notification.isRead());
    }
}
