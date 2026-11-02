package br.com.cactus.guard.notification.repository;

import br.com.cactus.guard.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    long countByUserIdAndReadFalse(UUID userId);
}
