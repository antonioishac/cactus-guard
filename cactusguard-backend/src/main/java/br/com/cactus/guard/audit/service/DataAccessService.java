package br.com.cactus.guard.audit.service;

import br.com.cactus.guard.audit.dto.AuditFilter;
import br.com.cactus.guard.audit.dto.DataAccessLogResponse;
import br.com.cactus.guard.audit.entity.DataAccessLog;
import br.com.cactus.guard.audit.repository.DataAccessLogRepository;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
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

    @Transactional(readOnly = true)
    public Page<DataAccessLogResponse> searchResponses(AuditFilter f, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<DataAccessLog> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> ps = new java.util.ArrayList<>();
            if (f.userId() != null) ps.add(cb.equal(root.get("user").get("id"), f.userId()));
            if (f.username() != null && !f.username().isBlank()) {
                ps.add(cb.equal(root.join("user", jakarta.persistence.criteria.JoinType.LEFT).get("username"), f.username().trim()));
            }
            if (f.entityType() != null && !f.entityType().isBlank()) ps.add(cb.equal(root.get("entityType"), f.entityType().trim()));
            if (f.entityId() != null) ps.add(cb.equal(root.get("entityId"), f.entityId()));
            if (f.from() != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            if (f.to() != null) ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), f.to()));
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Pageable p = pageable.getSort().isSorted() ? pageable : org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), org.springframework.data.domain.Sort.by("createdAt").descending());
        return repository.findAll(spec, p).map(DataAccessLogResponse::from);
    }
}
