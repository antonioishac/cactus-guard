package br.com.cactus.guard.notification;

import br.com.cactus.guard.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class NotificationCountTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void countNotificationsShouldBeNonNegative() {
        long count = notificationRepository.count();
        assertTrue(count >= 0);
    }
}
