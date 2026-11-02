package br.com.cactus.guard.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BusinessMetrics {

    private final MeterRegistry registry;

    public void alertCreated(String severity, String ruleCode) {
        Counter.builder("cactusguard.alerts.created")
                .tag("severity", severity)
                .tag("rule_code", ruleCode)
                .register(registry)
                .increment();
    }

    public void caseDecided(String decision) {
        Counter.builder("cactusguard.cases.decided")
                .tag("decision", decision)
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
}
