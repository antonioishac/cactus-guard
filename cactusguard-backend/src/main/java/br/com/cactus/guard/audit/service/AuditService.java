package br.com.cactus.guard.audit.service;

import br.com.cactus.guard.audit.entity.AuditLog;
import br.com.cactus.guard.audit.repository.AuditLogRepository;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void log(String action, String entityType, UUID entityId, Map<String, Object> oldValue, Map<String, Object> newValue) {
        User user = getCurrentUser();

        AuditLog log = AuditLog.builder()
                .user(user)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();

        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> findByEntity(String entityType, UUID entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
    }

    private User getCurrentUser() {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            return userRepository.findByUsername(username).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
