package br.com.cactus.guard.alert.repository;

import br.com.cactus.guard.alert.entity.Alert;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/** Filtros dinamicos de GET /alerts (todos opcionais, combinados com AND). */
public final class AlertSpecifications {

    private AlertSpecifications() {
    }

    public record Filter(Collection<String> statuses, Collection<String> severities, String type,
                         UUID assignedTo, Instant from, Instant toExclusive, UUID partyId, String ruleCode) {
    }

    public static Specification<Alert> from(Filter f) {
        return (root, query, cb) -> {
            var p = cb.conjunction();
            if (f.statuses() != null && !f.statuses().isEmpty()) {
                p = cb.and(p, root.get("status").in(f.statuses()));
            }
            if (f.severities() != null && !f.severities().isEmpty()) {
                p = cb.and(p, root.get("severity").in(f.severities()));
            }
            if (f.type() != null && !f.type().isBlank()) {
                p = cb.and(p, cb.equal(root.get("type"), f.type()));
            }
            if (f.assignedTo() != null) {
                p = cb.and(p, cb.equal(root.get("assignedTo").get("id"), f.assignedTo()));
            }
            if (f.from() != null) {
                p = cb.and(p, cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), f.from()));
            }
            if (f.toExclusive() != null) {
                p = cb.and(p, cb.lessThan(root.<Instant>get("createdAt"), f.toExclusive()));
            }
            if (f.partyId() != null) {
                p = cb.and(p, cb.equal(root.get("party").get("id"), f.partyId()));
            }
            if (f.ruleCode() != null && !f.ruleCode().isBlank()) {
                p = cb.and(p, cb.equal(root.get("ruleCode"), f.ruleCode()));
            }
            return p;
        };
    }
}
