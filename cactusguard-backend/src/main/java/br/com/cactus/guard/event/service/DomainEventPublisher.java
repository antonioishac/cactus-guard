package br.com.cactus.guard.event.service;

import br.com.cactus.guard.event.config.EventProperties;
import br.com.cactus.guard.event.dto.DomainEventType;
import br.com.cactus.guard.event.entity.OutboxEvent;
import br.com.cactus.guard.tenant.TenantContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ponto unico para emitir evento de dominio. Grava na tabela {@code outbox_events} NA MESMA transacao do servico de
 * negocio ({@code MANDATORY}): se a transacao faz rollback, o evento some junto; se comita, o evento e entregue
 * (at-least-once) pelo relay, mesmo com o Kafka fora do ar. Com {@code cactus.events.enabled=false} e noop.
 */
@Slf4j
@Service
public class DomainEventPublisher {

    public static final String HEADER_TENANT = "tenantId";
    public static final String HEADER_TRACE = "traceId";
    public static final String HEADER_EVENT_TYPE = "eventType";

    /** Header do outbox com o contexto W3C do request que originou o evento (continuado pelo relay e pelo consumidor). */
    public static final String HEADER_TRACEPARENT = "traceparent";

    private final EventProperties properties;
    private final Tracer tracer;
    private final Propagator propagator;

    public DomainEventPublisher(EventProperties properties, ObjectProvider<Tracer> tracer,
                                ObjectProvider<Propagator> propagator) {
        this.properties = properties;
        this.tracer = tracer.getIfAvailable();
        this.propagator = propagator.getIfAvailable();
    }

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * @param aggregateId chave de particao/ordenacao (ordem por agregado)
     * @param data        somente ids e campos nao pessoais (validado por {@link PiiGuard})
     * @return id do evento, ou {@code null} se os eventos estao desligados
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publish(DomainEventType type, UUID aggregateId, Map<String, Object> data) {
        if (!properties.isEnabled()) {
            return null;
        }
        PiiGuard.assertNoPii(data);
        UUID tenantId = TenantContext.require();
        Span span = tracer != null ? tracer.currentSpan() : null;
        String traceId = span != null ? span.context().traceId() : MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();

        OutboxEvent e = new OutboxEvent();
        e.setId(UUID.randomUUID());
        e.setAggregateType(type.aggregateType());
        e.setAggregateId(aggregateId);
        e.setEventType(type.type());
        e.setSchemaVersion(type.schemaVersion());
        e.setPayload(new LinkedHashMap<>(data));
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put(HEADER_TENANT, tenantId.toString());
        headers.put(HEADER_TRACE, traceId);
        headers.put(HEADER_EVENT_TYPE, type.type());
        if (span != null && propagator != null) {
            propagator.inject(span.context(), headers, (carrier, key, value) -> carrier.put(key, value));
        }
        e.setHeaders(headers);
        e.setOccurredAt(now);
        e.setNextAttemptAt(now);
        entityManager.persist(e);
        log.debug("Evento {} ({}) gravado no outbox para o agregado {}", e.getId(), type.type(), aggregateId);
        return e.getId();
    }
}
