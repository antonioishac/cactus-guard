package br.com.cactus.guard.audit.service;

import br.com.cactus.guard.audit.entity.DataAccessLog;
import br.com.cactus.guard.audit.repository.DataAccessLogRepository;
import br.com.cactus.guard.user.entity.User;
import br.com.cactus.guard.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DataAccessService {

    private final DataAccessLogRepository repository;
    private final UserRepository userRepository;

    @Transactional
    public void log(String entityType, UUID entityId, String fieldsAccessed, String reason, String ip) {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
        User user = username != null ? userRepository.findByUsername(username).orElse(null) : null;

        DataAccessLog log = DataAccessLog.builder()
                .user(user)
                .entityType(entityType)
                .entityId(entityId)
                .fieldsAccessed(fieldsAccessed)
                .accessReason(reason)
                .ipAddress(ip)
                .build();

        repository.save(log);
    }

    public Page<DataAccessLog> findByEntity(String entityType, UUID entityId, Pageable pageable) {
        return repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable);
    }

    public Page<DataAccessLog> findByUser(UUID userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}
