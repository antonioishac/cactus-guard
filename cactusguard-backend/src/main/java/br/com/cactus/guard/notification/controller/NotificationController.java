package br.com.cactus.guard.notification.controller;

import br.com.cactus.guard.notification.entity.Notification;
import br.com.cactus.guard.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<Notification>> findMy(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Pageable pageable) {
        return ResponseEntity.ok(notificationService.findMyNotifications(unreadOnly, pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> countUnread() {
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        notificationService.markAsRead(id);
        return ResponseEntity.noContent().build();
    }
}
