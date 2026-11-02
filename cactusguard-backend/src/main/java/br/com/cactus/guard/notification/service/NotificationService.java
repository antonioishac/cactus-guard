package br.com.cactus.guard.notification.service;

import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.notification.entity.Notification;
import br.com.cactus.guard.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public Notification create(UUID userId, String type, String title, String message, String entityType, UUID entityId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .entityType(entityType)
                .entityId(entityId)
                .read(false)
                .build();

        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<Notification> findMyNotifications(boolean unreadOnly, Pageable pageable) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (unreadOnly) {
            return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId(), pageable);
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
    }

    @Transactional
    public void markAsRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public long countUnread() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return 0;
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }
}
