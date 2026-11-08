package br.com.cactus.guard.event.service;

import br.com.cactus.guard.tenant.TenantContext;
import br.com.cactus.guard.tenant.service.TenantRunner;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backlog do outbox: {@code cactus.outbox.backlog} (eventos nao publicados) e
 * {@code cactus.outbox.oldest.age.seconds} (idade do mais antigo nao publicado; 0 sem backlog). Calculado por
 * tenant (RLS) em intervalo, nao a cada scrape.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cactus.events", name = "enabled", havingValue = "true")
public class OutboxMetrics {

    private record Stats(long backlog, Instant oldest) {
    }

    private final JdbcTemplate jdbc;
    private final TenantRunner tenantRunner;
    private final Map<UUID, Stats> byTenant = new ConcurrentHashMap<>();

    public OutboxMetrics(JdbcTemplate jdbc, TenantRunner tenantRunner, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.tenantRunner = tenantRunner;
        Gauge.builder("cactus.outbox.backlog", this, OutboxMetrics::totalBacklog)
                .description("Eventos do outbox ainda nao publicados").register(meters);
        Gauge.builder("cactus.outbox.oldest.age.seconds", this, OutboxMetrics::oldestAgeSeconds)
                .baseUnit("seconds").description("Idade do evento mais antigo nao publicado").register(meters);
    }

    @Scheduled(fixedDelayString = "${cactus.events.relay.metrics-interval:10s}")
    public void refresh() {
        tenantRunner.forEachActiveTenant("OutboxMetrics", tenantId ->
                jdbc.query("SELECT count(*) AS n, min(occurred_at) AS oldest FROM outbox_events "
                                + "WHERE tenant_id = ? AND published_at IS NULL",
                        rs -> {
                            Timestamp oldest = rs.getTimestamp("oldest");
                            byTenant.put(tenantId, new Stats(rs.getLong("n"), oldest == null ? null : oldest.toInstant()));
                        }, tenantId));
    }

    double totalBacklog() {
        return byTenant.values().stream().mapToLong(Stats::backlog).sum();
    }

    double oldestAgeSeconds() {
        Instant now = Instant.now();
        return byTenant.values().stream().map(Stats::oldest).filter(java.util.Objects::nonNull)
                .mapToDouble(o -> Duration.between(o, now).toMillis() / 1000.0).max().orElse(0);
    }
}
