package br.com.cactus.guard.common.calendar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Calendario de dias uteis para prazos regulatorios (Circular BCB 3.978/2020: comunicacao ao COAF
 * no dia util seguinte a decisao).
 * <p>
 * Feriados considerados: nacionais fixos (inclui 20/11, Consciencia Negra, nacional desde 2024),
 * moveis derivados da Pascoa (Carnaval segunda e terca, Sexta-feira Santa, Corpus Christi) e
 * feriados extras configuraveis em {@code cactus.calendar.extra-holidays} (lista ISO separada por
 * virgula, ex.: feriados estaduais/municipais da sede). Sabado e domingo nao sao dias uteis.
 * Carnaval e Corpus Christi sao tratados como nao uteis (calendario bancario/FEBRABAN) - decisao
 * conservadora: o prazo, se errado, e antecipado e nunca postergado.
 */
@Component
public class BusinessCalendar {

    public static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");

    private final Set<LocalDate> extraHolidays;

    @Autowired
    public BusinessCalendar(@Value("${cactus.calendar.extra-holidays:}") String extraHolidaysCsv) {
        this(parse(extraHolidaysCsv));
    }

    public BusinessCalendar(Set<LocalDate> extraHolidays) {
        this.extraHolidays = Set.copyOf(extraHolidays);
    }

    public static BusinessCalendar nationalOnly() {
        return new BusinessCalendar(Set.of());
    }

    private static Set<LocalDate> parse(String csv) {
        Set<LocalDate> out = new HashSet<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(s -> out.add(LocalDate.parse(s)));
        return out;
    }

    /** Domingo de Pascoa (algoritmo gregoriano anonimo / Meeus-Jones-Butcher). */
    public static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    public Set<LocalDate> holidays(int year) {
        Set<LocalDate> h = new HashSet<>();
        h.add(LocalDate.of(year, 1, 1));    // Confraternizacao Universal
        h.add(LocalDate.of(year, 4, 21));   // Tiradentes
        h.add(LocalDate.of(year, 5, 1));    // Dia do Trabalho
        h.add(LocalDate.of(year, 9, 7));    // Independencia
        h.add(LocalDate.of(year, 10, 12));  // Nossa Senhora Aparecida
        h.add(LocalDate.of(year, 11, 2));   // Finados
        h.add(LocalDate.of(year, 11, 15));  // Proclamacao da Republica
        if (year >= 2024) {
            h.add(LocalDate.of(year, 11, 20)); // Dia da Consciencia Negra (Lei 14.759/2023)
        }
        h.add(LocalDate.of(year, 12, 25));  // Natal
        LocalDate easter = easter(year);
        h.add(easter.minusDays(48));        // Carnaval - segunda
        h.add(easter.minusDays(47));        // Carnaval - terca
        h.add(easter.minusDays(2));         // Sexta-feira Santa
        h.add(easter.plusDays(60));         // Corpus Christi
        extraHolidays.stream().filter(d -> d.getYear() == year).forEach(h::add);
        return h;
    }

    public boolean isHoliday(LocalDate date) {
        return holidays(date.getYear()).contains(date);
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !isHoliday(date);
    }

    /** Primeiro dia util estritamente posterior a {@code date}. */
    public LocalDate nextBusinessDay(LocalDate date) {
        LocalDate d = date.plusDays(1);
        while (!isBusinessDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** Dia util seguinte ao instante (data calculada no fuso America/Sao_Paulo). */
    public LocalDate nextBusinessDay(Instant instant) {
        return nextBusinessDay(instant.atZone(BRAZIL).toLocalDate());
    }

    /** Fim do dia (23:59:59.999999, America/Sao_Paulo) do dia util seguinte: limite inclusivo do prazo. */
    public Instant nextBusinessDayDeadline(Instant instant) {
        return endOfDay(nextBusinessDay(instant));
    }

    /** Ultimo instante do dia com precisao de microssegundos (bancos arredondam nanos para cima e virariam o dia seguinte). */
    public static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59, 999_999_000);

    public static Instant endOfDay(LocalDate date) {
        return ZonedDateTime.of(date, END_OF_DAY, BRAZIL).toInstant();
    }

    /** Soma dias corridos no fuso do Brasil (sem deslocar horario em mudancas de offset). */
    public static Instant plusCalendarDays(Instant base, long days) {
        return base.atZone(BRAZIL).plusDays(days).toInstant();
    }
}
