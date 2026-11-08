package br.com.cactus.guard.rule.service;

import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.rule.dto.MonitoringRuleResponse;
import br.com.cactus.guard.rule.dto.RuleMetricsResponse;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.rule.repository.MonitoringRuleVersionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Consultas de regras: listagem paginada com filtros e metricas de calibracao (somente leitura). */
@Service
@RequiredArgsConstructor
public class RuleQueryService {

    /** Janela padrao das metricas. */
    public static final Duration DEFAULT_WINDOW = Duration.ofDays(30);
    private static final List<String> CLOSED_STATUSES = List.of("FALSE_POSITIVE", "CLOSED");

    private final MonitoringRuleRepository ruleRepository;
    private final MonitoringRuleVersionRepository versionRepository;
    private final AlertRepository alertRepository;
    private final MonitoringRuleService ruleService;

    public record Filter(Collection<String> statuses, String category, String severity, String q,
                         boolean includeInactive) {
    }

    /**
     * Lista regras. Sem {@code status} explicito, devolve so as ativas (comportamento anterior) a menos que
     * {@code includeInactive}; com {@code status} (ex.: PENDING_APPROVAL) o filtro explicito prevalece.
     */
    @Transactional(readOnly = true)
    public Page<MonitoringRuleResponse> list(Filter f, Pageable pageable) {
        Specification<MonitoringRule> spec = (root, query, cb) -> {
            Predicate p = cb.conjunction();
            if (f.statuses() != null && !f.statuses().isEmpty()) {
                p = cb.and(p, root.get("status").in(f.statuses()));
            } else if (!f.includeInactive()) {
                p = cb.and(p, cb.isTrue(root.<Boolean>get("active")));
            }
            if (f.category() != null && !f.category().isBlank()) {
                p = cb.and(p, cb.equal(root.get("category"), f.category()));
            }
            if (f.severity() != null && !f.severity().isBlank()) {
                p = cb.and(p, cb.equal(root.get("severity"), f.severity()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                p = cb.and(p, cb.or(cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like)));
            }
            return p;
        };
        Page<MonitoringRule> page = ruleRepository.findAll(spec, pageable);
        Instant to = Instant.now();
        Instant from = to.minus(DEFAULT_WINDOW);
        Map<String, long[]> stats = stats(page.getContent().stream().map(MonitoringRule::getCode).toList(), from, to);
        return page.map(r -> withMetrics(ruleService.toResponse(r), stats.get(r.getCode())));
    }

    /** Detalhe por codigo, com metricas (30 dias) e versao imutavel vigente. */
    @Transactional(readOnly = true)
    public MonitoringRuleResponse get(String code) {
        MonitoringRule rule = ruleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + code));
        Instant to = Instant.now();
        Map<String, long[]> stats = stats(List.of(code), to.minus(DEFAULT_WINDOW), to);
        MonitoringRuleResponse r = withMetrics(ruleService.toResponse(rule), stats.get(code));
        versionRepository.findEffectiveAt(rule.getId(), to).stream().findFirst()
                .ifPresent(v -> r.setActiveVersion(v.getVersion()));
        return r;
    }

    /** Metricas no periodo (padrao: ultimos 30 dias). */
    @Transactional(readOnly = true)
    public RuleMetricsResponse metrics(String code, Instant from, Instant to) {
        ruleRepository.findByCode(code).orElseThrow(() -> new IllegalArgumentException("Rule not found: " + code));
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(DEFAULT_WINDOW);
        long[] s = stats(List.of(code), start, end).getOrDefault(code, new long[3]);
        return new RuleMetricsResponse(code, start, end, s[0], s[1], s[2], rate(s[2], s[1]));
    }

    /** [gerados, encerrados, falsos positivos] por codigo, em uma unica consulta agregada. */
    private Map<String, long[]> stats(Collection<String> codes, Instant from, Instant to) {
        Map<String, long[]> out = new HashMap<>();
        if (codes.isEmpty()) {
            return out;
        }
        for (Object[] row : alertRepository.countByRuleAndStatus(codes, from, to)) {
            String code = (String) row[0];
            String status = (String) row[1];
            long n = ((Number) row[2]).longValue();
            long[] s = out.computeIfAbsent(code, k -> new long[3]);
            s[0] += n;
            if (CLOSED_STATUSES.contains(status)) {
                s[1] += n;
            }
            if ("FALSE_POSITIVE".equals(status)) {
                s[2] += n;
            }
        }
        return out;
    }

    private static MonitoringRuleResponse withMetrics(MonitoringRuleResponse r, long[] s) {
        long[] v = s != null ? s : new long[3];
        r.setAlertsGenerated(v[0]);
        r.setFalsePositiveRate(rate(v[2], v[1]));
        return r;
    }

    private static Double rate(long falsePositives, long closed) {
        return closed == 0 ? null : (double) falsePositives / closed;
    }
}
