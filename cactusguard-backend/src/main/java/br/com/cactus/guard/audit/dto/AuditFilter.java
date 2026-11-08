package br.com.cactus.guard.audit.dto;

import br.com.cactus.guard.common.calendar.BusinessCalendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Filtros server-side de auditoria / acesso a dados. {@code from}/{@code to} inclusivos; {@code action} so se
 * aplica a audit_log.
 */
public record AuditFilter(UUID userId, String username, String action, String entityType, UUID entityId,
                          Instant from, Instant to) {

    /**
     * Aceita data (yyyy-MM-dd, interpretada em America/Sao_Paulo; {@code to} vai ate o fim do dia) ou instante ISO-8601.
     */
    public static Instant parse(String value, boolean endOfPeriod) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            if (v.length() <= 10) {
                LocalDate d = LocalDate.parse(v);
                return (endOfPeriod ? d.plusDays(1) : d).atStartOfDay(BusinessCalendar.BRAZIL).toInstant()
                        .minusNanos(endOfPeriod ? 1 : 0);
            }
            return Instant.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("periodo invalido: use yyyy-MM-dd ou data/hora ISO-8601");
        }
    }
}
