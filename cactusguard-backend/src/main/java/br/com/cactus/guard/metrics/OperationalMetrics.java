package br.com.cactus.guard.metrics;

import br.com.cactus.guard.tenant.service.TenantRunner;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gauges operacionais calculados por snapshot periodico (por tenant, porque o RLS exige tenant; nao a cada scrape):
 * <ul>
 *   <li>{@code cactus.cases.by.status{status}} - casos por status;</li>
 *   <li>{@code cactus.dlq.pending} / {@code cactus.dlq.oldest.age.seconds} - DLQ pendente e idade da mais antiga.</li>
 * </ul>
 * Sem a tag {@code tenant} por padrao (soma da plataforma); com {@code cactus.metrics.tenant-tag.enabled} aparece
 * limitada por {@link MetricTags}. Series que somem (status sem casos) voltam a 0.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cactus.metrics.operational", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OperationalMetrics {

    private final JdbcTemplate jdbc;
    private final TenantRunner tenantRunner;
    private final MetricTags tenantTags;
    private final MeterRegistry meters;

    private final Map<Tags, AtomicReference<Double>> casesByStatus = new ConcurrentHashMap<>();
    private final Map<Tags, AtomicReference<Double>> dlqPending = new ConcurrentHashMap<>();
    private final Map<Tags, AtomicReference<Instant>> dlqOldest = new ConcurrentHashMap<>();

    public OperationalMetrics(JdbcTemplate jdbc, TenantRunner tenantRunner, MetricTags tenantTags, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.tenantRunner = tenantRunner;
        this.tenantTags = tenantTags;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${cactus.metrics.operational.refresh-interval:30s}",
            initialDelayString = "${cactus.metrics.operational.refresh-interval:30s}")
    public void refresh() {
        refreshNow();
    }

    /** Visivel para testes. */
    public void refreshNow() {
        Map<Tags, Double> cases = new HashMap<>();
        Map<Tags, Double> pending = new HashMap<>();
        Map<Tags, Instant> oldest = new HashMap<>();
        tenantRunner.forEachActiveTenant("OperationalMetrics", tenantId -> {
            Tags base = Tags.of(tenantTags.tenant(tenantId));
            jdbc.query("SELECT status, count(*) AS n FROM cases WHERE tenant_id = ? GROUP BY status", rs -> {
                cases.merge(base.and(Tag.of("status", rs.getString("status"))), rs.getDouble("n"), Double::sum);
            }, tenantId);
            jdbc.query("SELECT count(*) AS n, min(failed_at) AS oldest FROM dead_letter_events "
                    + "WHERE tenant_id = ? AND status = 'PENDING'", rs -> {
                pending.merge(base, rs.getDouble("n"), Double::sum);
                Timestamp o = rs.getTimestamp("oldest");
                if (o != null) {
                    oldest.merge(base, o.toInstant(), (a, b) -> a.isBefore(b) ? a : b);
                }
            }, tenantId);
        });
        publish(casesByStatus, cases, "cactus.cases.by.status", "Casos por status");
        publish(dlqPending, pending, "cactus.dlq.pending", "Mensagens na DLQ aguardando tratamento");
        // idade: guarda o instante e calcula na leitura (cresce entre refreshes)
        for (Map.Entry<Tags, Instant> e : oldest.entrySet()) {
            dlqOldest.computeIfAbsent(e.getKey(), k -> {
                AtomicReference<Instant> ref = new AtomicReference<>();
                Gauge.builder("cactus.dlq.oldest.age.seconds", ref, r -> {
                    Instant o = r.get();
                    return o == null ? 0 : Duration.between(o, Instant.now()).toMillis() / 1000.0;
                }).tags(k).baseUnit("seconds").description("Idade da mensagem pendente mais antiga da DLQ").register(meters);
                return ref;
            }).set(e.getValue());
        }
        dlqOldest.forEach((k, ref) -> {
            if (!oldest.containsKey(k)) {
                ref.set(null);
            }
        });
    }

    private void publish(Map<Tags, AtomicReference<Double>> store, Map<Tags, Double> values, String name, String help) {
        for (Map.Entry<Tags, Double> e : values.entrySet()) {
            store.computeIfAbsent(e.getKey(), k -> {
                AtomicReference<Double> ref = new AtomicReference<>(0.0);
                Gauge.builder(name, ref, AtomicReference::get).tags(k).description(help).register(meters);
                return ref;
            }).set(e.getValue());
        }
        store.forEach((k, ref) -> {
            if (!values.containsKey(k)) {
                ref.set(0.0);
            }
        });
    }
}
