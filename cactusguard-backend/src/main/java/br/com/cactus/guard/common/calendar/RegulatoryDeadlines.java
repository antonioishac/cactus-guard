package br.com.cactus.guard.common.calendar;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Prazos da Circular BCB 3.978/2020: 45 dias para selecao (da ocorrencia), 45 dias para analise
 * (da selecao) e comunicacao ao COAF ate o dia util seguinte a decisao.
 */
@Component
@RequiredArgsConstructor
public class RegulatoryDeadlines {

    public static final int SELECTION_DAYS = 45;
    public static final int ANALYSIS_DAYS = 45;

    private final BusinessCalendar calendar;

    public Instant selectionDeadline(Instant occurredAt) {
        return BusinessCalendar.plusCalendarDays(occurredAt, SELECTION_DAYS);
    }

    public Instant analysisDeadline(Instant selectedAt) {
        return BusinessCalendar.plusCalendarDays(selectedAt, ANALYSIS_DAYS);
    }

    public Instant communicationDeadline(Instant decidedAt) {
        return calendar.nextBusinessDayDeadline(decidedAt);
    }

    /** SLA interno nunca pode exceder o prazo regulatorio. */
    public static Instant min(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }
}
