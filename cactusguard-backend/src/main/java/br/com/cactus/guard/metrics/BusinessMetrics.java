package br.com.cactus.guard.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Metricas de negocio (spec 038). Tags de baixa cardinalidade; {@code tenant} so quando habilitado e limitado
 * ({@link MetricTags}).
 */
@Component
@RequiredArgsConstructor
public class BusinessMetrics {

    public static final String RULES_EVALUATION = "cactus.rules.evaluation.duration";

    private final MeterRegistry registry;
    private final MetricTags tenantTags;

    public void alertCreated(String severity, String ruleCode) {
        Counter.builder("cactusguard.alerts.created")
                .tag("severity", severity == null ? "UNKNOWN" : severity)
                .tag("rule_code", ruleCode == null ? "NONE" : ruleCode)
                .tags(Tags.of(tenantTags.tenant()))
                .register(registry)
                .increment();
    }

    public void caseDecided(String decision) {
        Counter.builder("cactusguard.cases.decided")
                .tag("decision", decision)
                .tags(Tags.of(tenantTags.tenant()))
                .register(registry)
                .increment();
    }

    public void kycCompleted(String status) {
        Counter.builder("cactusguard.kyc.completed")
                .tag("status", status)
                .register(registry)
                .increment();
    }

    public void screeningMatch(String listCode, String status) {
        Counter.builder("cactusguard.screening.matches")
                .tag("list_code", listCode)
                .tag("status", status)
                .register(registry)
                .increment();
    }

    /**
     * Latencia da avaliacao de regras de UMA transacao (consumidor por evento). Histograma publicado para
     * {@code histogram_quantile} (p99 do SLO) e SLO buckets em 100ms/250ms/500ms/1s/2s.
     */
    public void transactionEvaluated(Duration elapsed, boolean success) {
        Timer.builder(RULES_EVALUATION)
                .description("Latencia da avaliacao das regras por transacao")
                .tag("outcome", success ? "success" : "error")
                .tags(Tags.of(tenantTags.tenant()))
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(250), Duration.ofMillis(500),
                        Duration.ofSeconds(1), Duration.ofSeconds(2))
                .register(registry)
                .record(elapsed);
    }
}
