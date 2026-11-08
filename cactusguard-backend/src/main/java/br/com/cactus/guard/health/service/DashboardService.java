package br.com.cactus.guard.health.service;

import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.case_.service.SlaCalculator;
import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Indicadores do dashboard (spec 045/031). Somente consultas agregadas no banco (COUNT / GROUP BY);
 * nenhuma entidade e carregada em memoria.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** Alertas ainda em tratamento. */
    static final Set<String> ACTIVE_ALERTS = Set.of("OPEN", "IN_ANALYSIS", "ESCALATED");
    private static final Duration SELECTION_WARN = Duration.ofDays(5);
    private static final List<String> SEVERITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final List<String> ALERT_STATUSES = List.of("OPEN", "IN_ANALYSIS", "ESCALATED", "FALSE_POSITIVE", "CLOSED");
    public static final int MAX_CHART_DAYS = 90;

    private final AlertRepository alertRepository;
    private final CaseRepository caseRepository;
    private final ScreeningResultRepository screeningRepository;

    @PersistenceContext
    private EntityManager em;

    public record Buckets(long onTime, long dueSoon, long overdue) {
    }

    public record CaseSla(Buckets selection, Buckets analysis, Buckets communication, long pendingApproval) {
    }

    public record ChartPoint(String date, long total) {
    }

    public record SeverityCount(String severity, long count) {
    }

    @Transactional(readOnly = true)
    public Map<String, Object> metrics() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        ALERT_STATUSES.forEach(s -> byStatus.put(s, 0L));
        for (Object[] row : alertRepository.countGroupedByStatus()) {
            byStatus.put((String) row[0], ((Number) row[1]).longValue());
        }
        long activePeps = em.createQuery(
                        "SELECT COUNT(p) FROM Party p WHERE p.pep = true AND p.pepStatus IN :st", Long.class)
                .setParameter("st", List.of("ACTIVE", "RELATED")).getSingleResult();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alertsByStatus", byStatus);
        out.put("screenings", screeningRepository.countByStatus("PENDING"));
        out.put("activePeps", activePeps);
        out.put("openCases", caseRepository.countByStatusIn(SlaCalculator.ANALYSIS_STATUSES));
        out.put("pendingApproval", caseRepository.countByStatus("PENDING_APPROVAL"));
        return out;
    }

    /** Alertas criados por dia nos ultimos {@code days} dias (inclusive hoje), com zeros nos dias vazios. */
    @Transactional(readOnly = true)
    public List<ChartPoint> alertsChart(int days) {
        int n = Math.max(1, Math.min(days, MAX_CHART_DAYS));
        LocalDate today = LocalDate.now(BusinessCalendar.BRAZIL);
        LocalDate first = today.minusDays(n - 1L);
        Instant since = first.atStartOfDay(BusinessCalendar.BRAZIL).toInstant();
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (Object[] row : alertRepository.countCreatedPerDaySince(since)) {
            counts.merge(LocalDate.parse(String.valueOf(row[0])), ((Number) row[1]).longValue(), Long::sum);
        }
        List<ChartPoint> out = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            out.add(new ChartPoint(d.toString(), counts.getOrDefault(d, 0L)));
        }
        return out;
    }

    /** Alertas em tratamento por severidade (todas as severidades, inclusive com zero). */
    @Transactional(readOnly = true)
    public List<SeverityCount> severityDistribution() {
        Map<String, Long> counts = new LinkedHashMap<>();
        SEVERITIES.forEach(s -> counts.put(s, 0L));
        for (Object[] row : alertRepository.countGroupedBySeverity(ACTIVE_ALERTS)) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        return counts.entrySet().stream().map(e -> new SeverityCount(e.getKey(), e.getValue())).toList();
    }

    /**
     * Situacao dos prazos regulatorios: selecao (alertas ainda nao selecionados, 45 dias da ocorrencia),
     * analise (cases em tratamento, 45 dias da selecao) e comunicacao (cases aprovados para comunicar, D+1 util).
     */
    @Transactional(readOnly = true)
    public CaseSla caseSla() {
        Instant now = Instant.now();

        long selTotal = alertRepository.countUnselected(ACTIVE_ALERTS);
        long selOver = alertRepository.countUnselectedOverdue(ACTIVE_ALERTS, now);
        long selSoon = alertRepository.countUnselectedDueBetween(
                ACTIVE_ALERTS, now, now.plus(SELECTION_WARN)) ;

        Set<String> analysis = SlaCalculator.ANALYSIS_STATUSES;
        long anTotal = caseRepository.countByStatusIn(analysis);
        long anOver = caseRepository.countByStatusInAndAnalysisDeadlineBefore(analysis, now);
        long anSoon = caseRepository.countByStatusInAndAnalysisDeadlineBetween(
                analysis, now, now.plus(SlaCalculator.ANALYSIS_WARN));

        String comm = SlaCalculator.COMMUNICATE_STATUS;
        long cmTotal = caseRepository.countByStatus(comm);
        long cmOver = caseRepository.countByStatusAndCommunicationDeadlineBefore(comm, now);
        long cmSoon = caseRepository.countByStatusAndCommunicationDeadlineBetween(
                comm, now, now.plus(SlaCalculator.COMMUNICATION_WARN));

        return new CaseSla(buckets(selTotal, selSoon, selOver), buckets(anTotal, anSoon, anOver),
                buckets(cmTotal, cmSoon, cmOver), caseRepository.countByStatus("PENDING_APPROVAL"));
    }

    private static Buckets buckets(long total, long soon, long overdue) {
        return new Buckets(Math.max(0, total - soon - overdue), soon, overdue);
    }
}
