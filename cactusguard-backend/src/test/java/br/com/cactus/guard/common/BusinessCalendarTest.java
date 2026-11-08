package br.com.cactus.guard.common;

import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.common.calendar.RegulatoryDeadlines;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BusinessCalendarTest {

    private final BusinessCalendar cal = BusinessCalendar.nationalOnly();

    @Test
    void easterDatesAreCorrect() {
        assertEquals(LocalDate.of(2024, 3, 31), BusinessCalendar.easter(2024));
        assertEquals(LocalDate.of(2025, 4, 20), BusinessCalendar.easter(2025));
        assertEquals(LocalDate.of(2026, 4, 5), BusinessCalendar.easter(2026));
    }

    @Test
    void movableHolidays2026() {
        assertTrue(cal.isHoliday(LocalDate.of(2026, 2, 16)), "Carnaval segunda");
        assertTrue(cal.isHoliday(LocalDate.of(2026, 2, 17)), "Carnaval terca");
        assertTrue(cal.isHoliday(LocalDate.of(2026, 4, 3)), "Sexta-feira Santa");
        assertTrue(cal.isHoliday(LocalDate.of(2026, 6, 4)), "Corpus Christi");
        assertFalse(cal.isHoliday(LocalDate.of(2026, 2, 18)), "Quarta de cinzas e dia util");
    }

    @Test
    void fixedNationalHolidays() {
        for (LocalDate d : new LocalDate[]{
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 21), LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 25)}) {
            assertTrue(cal.isHoliday(d), d.toString());
        }
    }

    @Test
    void consciousnessDayOnlyFrom2024() {
        assertFalse(cal.isHoliday(LocalDate.of(2023, 11, 20)));
        assertTrue(cal.isHoliday(LocalDate.of(2024, 11, 20)));
    }

    @Test
    void nextBusinessDaySkipsWeekendAndHolidays() {
        // quinta antes da Sexta-feira Santa -> segunda
        assertEquals(LocalDate.of(2026, 4, 6), cal.nextBusinessDay(LocalDate.of(2026, 4, 2)));
        // sexta comum -> segunda
        assertEquals(LocalDate.of(2025, 6, 2), cal.nextBusinessDay(LocalDate.of(2025, 5, 30)));
        // vespera de Natal -> 26
        assertEquals(LocalDate.of(2025, 12, 26), cal.nextBusinessDay(LocalDate.of(2025, 12, 24)));
        // vespera de Corpus Christi 2025 (19/06) -> 20/06
        assertEquals(LocalDate.of(2025, 6, 20), cal.nextBusinessDay(LocalDate.of(2025, 6, 18)));
        // Carnaval 2026: sexta 13/02 -> quarta 18/02
        assertEquals(LocalDate.of(2026, 2, 18), cal.nextBusinessDay(LocalDate.of(2026, 2, 13)));
        // consciencia negra 2024 (quarta 20/11) -> 21/11
        assertEquals(LocalDate.of(2024, 11, 21), cal.nextBusinessDay(LocalDate.of(2024, 11, 19)));
        // sabado -> segunda
        assertEquals(LocalDate.of(2026, 11, 9), cal.nextBusinessDay(LocalDate.of(2026, 11, 7)));
    }

    @Test
    void nextBusinessDayIsStrictlyAfter() {
        LocalDate friday = LocalDate.of(2026, 11, 6);
        assertTrue(cal.isBusinessDay(friday));
        assertEquals(LocalDate.of(2026, 11, 9), cal.nextBusinessDay(friday));
    }

    @Test
    void extraHolidaysAreConfigurable() {
        BusinessCalendar custom = new BusinessCalendar(Set.of(LocalDate.of(2026, 7, 9)));
        assertTrue(custom.isHoliday(LocalDate.of(2026, 7, 9)));
        assertEquals(LocalDate.of(2026, 7, 10), custom.nextBusinessDay(LocalDate.of(2026, 7, 8)));
        assertEquals(LocalDate.of(2026, 7, 9), cal.nextBusinessDay(LocalDate.of(2026, 7, 8)));
        // CSV via construtor Spring
        BusinessCalendar csv = new BusinessCalendar("2026-07-09, 2026-07-10");
        assertEquals(LocalDate.of(2026, 7, 13), csv.nextBusinessDay(LocalDate.of(2026, 7, 8)));
    }

    @Test
    void deadlineUsesSaoPauloEndOfDay() {
        // 23:30 em Sao Paulo de uma sexta ainda e sexta (02:30Z de sabado)
        Instant fridayNight = ZonedDateTime.of(2026, 11, 6, 23, 30, 0, 0, BusinessCalendar.BRAZIL).toInstant();
        Instant deadline = cal.nextBusinessDayDeadline(fridayNight);
        ZonedDateTime z = deadline.atZone(BusinessCalendar.BRAZIL);
        assertEquals(LocalDate.of(2026, 11, 9), z.toLocalDate());
        assertEquals(23, z.getHour());
        assertEquals(59, z.getMinute());
    }

    @Test
    void regulatoryDeadlines45Days() {
        RegulatoryDeadlines d = new RegulatoryDeadlines(cal);
        Instant t = Instant.parse("2026-01-10T12:00:00Z");
        assertEquals(Instant.parse("2026-02-24T12:00:00Z"), d.selectionDeadline(t));
        assertEquals(Instant.parse("2026-02-24T12:00:00Z"), d.analysisDeadline(t));
        assertEquals(t.plusSeconds(10), RegulatoryDeadlines.min(t.plusSeconds(10), t.plusSeconds(20)));
        assertEquals(t, RegulatoryDeadlines.min(null, t));
    }
}
