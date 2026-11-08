package br.com.cactus.guard.case_.repository;

import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.service.SlaCalculator;
import br.com.cactus.guard.common.exception.BusinessException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/** Filtros dinamicos de GET /cases (todos opcionais, AND). slaStatus e calculado em SQL a partir dos prazos. */
public final class CaseSpecifications {

    private CaseSpecifications() {
    }

    public record Filter(Collection<String> statuses, UUID assignedTo, String slaStatus, String riskCategory,
                         Boolean edd, Instant from, Instant toExclusive, UUID partyId) {
    }

    public static Specification<Case> from(Filter f, Instant now) {
        return (root, query, cb) -> {
            Predicate p = cb.conjunction();
            if (f.statuses() != null && !f.statuses().isEmpty()) {
                p = cb.and(p, root.get("status").in(f.statuses()));
            }
            if (f.assignedTo() != null) {
                p = cb.and(p, cb.equal(root.get("assignedTo").get("id"), f.assignedTo()));
            }
            if (f.partyId() != null) {
                p = cb.and(p, cb.equal(root.get("party").get("id"), f.partyId()));
            }
            if (f.edd() != null) {
                p = cb.and(p, cb.equal(root.get("edd"), f.edd()));
            }
            if (f.riskCategory() != null && !f.riskCategory().isBlank()) {
                if ("PEP".equalsIgnoreCase(f.riskCategory())) {
                    p = cb.and(p, cb.isTrue(root.get("party").<Boolean>get("pep")));
                } else {
                    p = cb.and(p, cb.equal(root.get("party").get("riskLevel"),
                            f.riskCategory().toUpperCase(Locale.ROOT)));
                }
            }
            if (f.from() != null) {
                p = cb.and(p, cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), f.from()));
            }
            if (f.toExclusive() != null) {
                p = cb.and(p, cb.lessThan(root.<Instant>get("createdAt"), f.toExclusive()));
            }
            if (f.slaStatus() != null && !f.slaStatus().isBlank()) {
                p = cb.and(p, sla(f.slaStatus().toUpperCase(Locale.ROOT), root, cb, now));
            }
            return p;
        };
    }

    private static Predicate sla(String sla, Root<Case> root, CriteriaBuilder cb, Instant now) {
        var status = root.<String>get("status");
        var analysis = root.<Instant>get("analysisDeadline");
        var comm = root.<Instant>get("communicationDeadline");
        Predicate inAnalysis = status.in(SlaCalculator.ANALYSIS_STATUSES);
        Predicate inComm = cb.equal(status, SlaCalculator.COMMUNICATE_STATUS);
        Instant analysisSoon = now.plus(SlaCalculator.ANALYSIS_WARN);
        Instant commSoon = now.plus(SlaCalculator.COMMUNICATION_WARN);

        Predicate analysisOverdue = cb.and(inAnalysis, cb.isNotNull(analysis), cb.lessThan(analysis, now));
        Predicate commOverdue = cb.and(inComm, cb.isNotNull(comm), cb.lessThan(comm, now));
        Predicate analysisSoonP = cb.and(inAnalysis, cb.isNotNull(analysis),
                cb.greaterThanOrEqualTo(analysis, now), cb.lessThan(analysis, analysisSoon));
        Predicate commSoonP = cb.and(inComm, cb.isNotNull(comm),
                cb.greaterThanOrEqualTo(comm, now), cb.lessThan(comm, commSoon));

        return switch (sla) {
            case SlaCalculator.OVERDUE -> cb.or(analysisOverdue, commOverdue);
            case SlaCalculator.DUE_SOON -> cb.or(analysisSoonP, commSoonP);
            case SlaCalculator.ON_TIME -> cb.and(cb.or(inAnalysis, inComm),
                    cb.not(cb.or(analysisOverdue, commOverdue, analysisSoonP, commSoonP)));
            default -> throw new BusinessException("slaStatus invalido: use ON_TIME, DUE_SOON ou OVERDUE");
        };
    }
}
