package br.com.cactus.guard.common.util;

import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.common.exception.BusinessException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Intervalo de filtro {@code from}/{@code to} recebido por query string. Aceita data ({@code 2026-11-08},
 * interpretada no fuso de Brasilia; {@code to} e inclusivo ate o fim do dia) ou instante ISO-8601.
 * Limites: {@code from} inclusivo, {@code toExclusive} exclusivo.
 */
public record DateRange(Instant from, Instant toExclusive) {

    public static DateRange of(String from, String to) {
        Instant f = from == null || from.isBlank() ? null : parse(from.trim(), false);
        Instant t = to == null || to.isBlank() ? null : parse(to.trim(), true);
        if (f != null && t != null && !t.isAfter(f)) {
            throw new BusinessException("Periodo invalido: 'to' deve ser posterior a 'from'");
        }
        return new DateRange(f, t);
    }

    private static Instant parse(String v, boolean endOfDay) {
        try {
            if (v.length() <= 10) {
                LocalDate d = LocalDate.parse(v);
                return (endOfDay ? d.plusDays(1) : d).atStartOfDay(BusinessCalendar.BRAZIL).toInstant();
            }
            return Instant.parse(v);
        } catch (DateTimeParseException e) {
            throw new BusinessException("Data invalida: use AAAA-MM-DD ou ISO-8601");
        }
    }
}
