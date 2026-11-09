package br.com.cactus.guard.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;

/**
 * Atributo {@code tenant.id} (UUID, nunca PII) em TODOS os spans (spec 038):
 * <ul>
 *   <li>{@link SpanProcessor} do SDK OTel: todo span, inclusive de bibliotecas (Redis, Kafka, RestClient), recebe o
 *       tenant da thread no inicio;</li>
 *   <li>hook {@link TenantTracing}: o span que ja estava em andamento quando o tenant e definido (HTTP, apos o JWT;
 *       consumidor Kafka, dentro do {@code runAs}) e etiquetado no momento da troca;</li>
 *   <li>span HTTP raiz: etiquetado em {@code JwtAuthenticationFilter} via contexto da observacao.</li>
 * </ul>
 * Nunca vira tag de metrica (alta cardinalidade).
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class TenantSpanConfig {

    private final ObjectProvider<Tracer> tracerProvider;

    @PostConstruct
    void installHook() {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return;
        }
        TenantTracing.install(tenantId -> {
            Span span = tracer.currentSpan();
            if (span != null) {
                span.tag(TenantTracing.ATTRIBUTE, tenantId.toString());
            }
        });
    }
}
