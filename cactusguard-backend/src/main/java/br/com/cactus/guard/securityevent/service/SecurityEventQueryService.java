package br.com.cactus.guard.securityevent.service;

import br.com.cactus.guard.securityevent.dto.SecurityEventResponse;
import br.com.cactus.guard.securityevent.entity.SecurityEvent;
import br.com.cactus.guard.securityevent.repository.SecurityEventRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityEventQueryService {

    private final SecurityEventRepository repository;
    private final LoginGuard loginGuard;

    /** Todos os filtros sao opcionais. {@code username} e comparado pelo hash (o valor em claro nao e armazenado). */
    @Transactional(readOnly = true)
    public Page<SecurityEventResponse> search(String eventType, String result, String username, String ip,
                                              UUID tenantId, Instant from, Instant to, Pageable pageable) {
        String usernameHash = username == null || username.isBlank() ? null : loginGuard.usernameHash(username);
        Specification<SecurityEvent> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (eventType != null && !eventType.isBlank()) p.add(cb.equal(root.get("eventType"), eventType));
            if (result != null && !result.isBlank()) p.add(cb.equal(root.get("result"), result));
            if (usernameHash != null) p.add(cb.equal(root.get("usernameHash"), usernameHash));
            if (ip != null && !ip.isBlank()) p.add(cb.equal(root.get("ipAddress"), ip));
            if (tenantId != null) p.add(cb.equal(root.get("tenantId"), tenantId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable).map(SecurityEventResponse::from);
    }
}
