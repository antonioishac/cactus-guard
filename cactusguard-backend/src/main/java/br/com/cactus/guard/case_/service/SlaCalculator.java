package br.com.cactus.guard.case_.service;

import br.com.cactus.guard.case_.entity.Case;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Estado do prazo regulatorio de um case (Circular 3.978): em analise, vale o prazo de analise
 * (selecao + 45 dias); com decisao COMMUNICATE aprovada, o prazo de comunicacao (D+1 util da decisao).
 * Case encerrado (ARCHIVED/COMMUNICATED) nao tem SLA (null).
 */
public final class SlaCalculator {

    public static final String ON_TIME = "ON_TIME";
    public static final String DUE_SOON = "DUE_SOON";
    public static final String OVERDUE = "OVERDUE";

    /** Janela de "vencendo" na analise (5 dias) e na comunicacao (12 horas). */
    public static final Duration ANALYSIS_WARN = Duration.ofDays(5);
    public static final Duration COMMUNICATION_WARN = Duration.ofHours(12);

    /** Status em que o prazo vigente e o de analise. */
    public static final Set<String> ANALYSIS_STATUSES =
            Set.of("OPEN", "IN_ANALYSIS", "PENDING_DECISION", "ESCALATED", "PENDING_APPROVAL");
    public static final String COMMUNICATE_STATUS = "COMMUNICATE";

    private SlaCalculator() {
    }

    public static String classify(Case c, Instant now) {
        return classify(c.getStatus(), c.getAnalysisDeadline(), c.getCommunicationDeadline(), now);
    }

    public static String classify(String status, Instant analysisDeadline, Instant communicationDeadline, Instant now) {
        if (COMMUNICATE_STATUS.equals(status)) {
            return classify(communicationDeadline, COMMUNICATION_WARN, now);
        }
        if (ANALYSIS_STATUSES.contains(status)) {
            return classify(analysisDeadline, ANALYSIS_WARN, now);
        }
        return null;
    }

    private static String classify(Instant deadline, Duration warn, Instant now) {
        if (deadline == null) {
            return ON_TIME;
        }
        if (deadline.isBefore(now)) {
            return OVERDUE;
        }
        return deadline.isBefore(now.plus(warn)) ? DUE_SOON : ON_TIME;
    }
}
