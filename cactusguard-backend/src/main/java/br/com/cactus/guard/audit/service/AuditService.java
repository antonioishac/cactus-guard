package br.com.cactus.guard.audit.service;

import br.com.cactus.guard.audit.dto.AuditFilter;
import br.com.cactus.guard.audit.dto.AuditLogResponse;
import br.com.cactus.guard.audit.entity.AuditLog;
import br.com.cactus.guard.common.audit.AuditPayload;
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
                .oldValue(AuditPayload.normalize(oldValue))
                .newValue(AuditPayload.normalize(newValue))
                .build();

        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> findByEntity(String entityType, UUID entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
    }

    /** Consulta global paginada com filtros server-side (usuario, acao, entidade, periodo); mais recentes primeiro. */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(AuditFilter f, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<AuditLog> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> ps = new java.util.ArrayList<>();
            if (f.userId() != null) ps.add(cb.equal(root.get("user").get("id"), f.userId()));
            if (f.username() != null && !f.username().isBlank()) {
                ps.add(cb.equal(root.join("user", jakarta.persistence.criteria.JoinType.LEFT).get("username"), f.username().trim()));
            }
            if (f.action() != null && !f.action().isBlank()) ps.add(cb.equal(root.get("action"), f.action().trim()));
            if (f.entityType() != null && !f.entityType().isBlank()) ps.add(cb.equal(root.get("entityType"), f.entityType().trim()));
            if (f.entityId() != null) ps.add(cb.equal(root.get("entityId"), f.entityId()));
            if (f.from() != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            if (f.to() != null) ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), f.to()));
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Pageable p = pageable.getSort().isSorted() ? pageable : org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), org.springframework.data.domain.Sort.by("createdAt").descending());
        return auditLogRepository.findAll(spec, p).map(AuditLogResponse::from);
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
