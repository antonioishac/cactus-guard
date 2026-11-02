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
class NotificationFindMyTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findMyNotificationsShouldReturnPage() {
        var user = userRepository.findByUsername("admin").orElse(null);
        if (user == null) return;

        notificationService.create(user.getId(), "FIND_MY", "Find My Test", "Msg", "TEST", UUID.randomUUID());

        var page = notificationService.findMyNotifications(false, org.springframework.data.domain.Pageable.unpaged());
        assertNotNull(page);
    }
}
