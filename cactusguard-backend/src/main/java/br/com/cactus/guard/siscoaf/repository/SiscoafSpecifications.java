package br.com.cactus.guard.siscoaf.repository;

import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/** Filtros de GET /siscoaf (todos opcionais, AND). Periodo sobre a data de criacao. */
public final class SiscoafSpecifications {

    private SiscoafSpecifications() {
    }

    public record Filter(Collection<String> statuses, Collection<String> types, Instant from,
                         Instant toExclusive, UUID partyId, UUID caseId) {
    }

    public static Specification<SiscoafCommunication> from(Filter f) {
        return (root, query, cb) -> {
            Predicate p = cb.conjunction();
            if (f.statuses() != null && !f.statuses().isEmpty()) {
                p = cb.and(p, root.get("status").in(f.statuses()));
            }
            if (f.types() != null && !f.types().isEmpty()) {
                p = cb.and(p, root.get("type").in(f.types()));
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
            if (f.caseId() != null) {
                p = cb.and(p, cb.equal(root.get("caseEntity").get("id"), f.caseId()));
            }
            return p;
        };
    }
}
