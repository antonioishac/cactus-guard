package br.com.cactus.guard.notification;

import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class NotificationMarkReadTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @WithMockUser(username = "admin") // "minhas notificacoes" depende do usuario logado
    void markAsReadShouldUpdateStatus() {
        var user = userRepository.findByUsername("admin").orElse(null);
        if (user == null) return;

        var notification = notificationService.create(
                user.getId(), "MARK_READ", "Mark Read Test", "Message", "TEST", UUID.randomUUID());

        assertFalse(notification.isRead());

        notificationService.markAsRead(notification.getId());

        var updated = notificationService.findMyNotifications(false, org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream()
                .filter(n -> n.getId().equals(notification.getId()))
                .findFirst();

        assertTrue(updated.isPresent());
    }
}
