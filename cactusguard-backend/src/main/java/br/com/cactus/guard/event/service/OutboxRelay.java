package br.com.cactus.guard.event.service;

import br.com.cactus.guard.event.config.EventProperties;
import br.com.cactus.guard.event.dto.DomainEventType;
import br.com.cactus.guard.event.dto.EventEnvelope;
import br.com.cactus.guard.tenant.TenantContext;
import br.com.cactus.guard.tenant.service.TenantRunner;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Relay do outbox: publica no Kafka os eventos gravados pelo negocio (at-least-once).
 * <ul>
 *   <li>Por tenant (RLS exige tenant): {@code SELECT ... FOR UPDATE SKIP LOCKED} em lote, varias instancias
 *       convivem sem pegar a mesma linha.</li>
 *   <li>So a CABECA de cada agregado e elegivel (nenhum evento anterior do mesmo agregado pendente): preserva a ordem
 *       por agregado inclusive com backoff e varias instancias.</li>
 *   <li>Producer {@code acks=all} idempotente; sucesso marca {@code published_at}; falha incrementa attempts e agenda
 *       a proxima tentativa com backoff exponencial.</li>
 *   <li>Circuit breaker (Resilience4j) em torno do broker: aberto, o ciclo e pulado sem gastar tentativas e sem
 *       bloquear a thread em timeouts.</li>
 * </ul>
 * Pode publicar duplicado (queda entre o envio e o commit): os consumidores deduplicam por id do evento.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "cactus.events", name = "enabled", havingValue = "true")
public class OutboxRelay {

    static final String SELECT_BATCH = """
            SELECT o.id, o.aggregate_id, o.event_type, o.schema_version, o.payload::text AS payload,
                   o.headers::text AS headers, o.occurred_at, o.attempts
              FROM outbox_events o
             WHERE o.tenant_id = ? AND o.published_at IS NULL AND o.next_attempt_at <= now()
               AND NOT EXISTS (SELECT 1 FROM outbox_events p
                                WHERE p.aggregate_id = o.aggregate_id AND p.tenant_id = o.tenant_id
                                  AND p.published_at IS NULL
                                  AND (p.occurred_at < o.occurred_at
                                       OR (p.occurred_at = o.occurred_at AND p.id < o.id)))
             ORDER BY o.occurred_at, o.id
             LIMIT ?
             FOR UPDATE OF o SKIP LOCKED
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final EventProperties props;
    private final TenantRunner tenantRunner;
    private final CircuitBreaker breaker;
    private final Counter published;
    private final Counter failed;
    private final Tracer tracer;
    private final Propagator propagator;

    public OutboxRelay(JdbcTemplate jdbc, PlatformTransactionManager txManager, KafkaTemplate<String, String> kafka,
                       ObjectMapper mapper, EventProperties props, TenantRunner tenantRunner,
                       CircuitBreakerRegistry breakers, MeterRegistry meters,
                       ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer.getIfAvailable();
        this.propagator = propagator.getIfAvailable();
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.kafka = kafka;
        this.mapper = mapper;
        this.props = props;
        this.tenantRunner = tenantRunner;
        this.breaker = breakers.circuitBreaker("kafka-outbox");
        this.published = Counter.builder("cactus.outbox.published").description("Eventos publicados no Kafka").register(meters);
        this.failed = Counter.builder("cactus.outbox.publish.failures").description("Falhas de publicacao").register(meters);
    }

    @Scheduled(fixedDelayString = "${cactus.events.relay.poll-interval:500ms}")
    public void poll() {
        if (!props.getRelay().isEnabled()) {
            return;
        }
        relayOnce();
    }

    /** Um ciclo completo sobre todos os tenants ativos. Devolve quantos eventos foram publicados. */
    public int relayOnce() {
        int total = 0;
        for (UUID tenantId : tenantRunner.activeTenantIds()) {
            if (breaker.getState() == CircuitBreaker.State.OPEN) {
                log.debug("Circuit breaker do Kafka aberto: ciclo do relay adiado");
                return total;
            }
            try {
                int n;
                do {
                    n = TenantContext.callAs(tenantId, () -> publishBatch(tenantId));
                    total += n;
                } while (n >= props.getRelay().getBatchSize());
            } catch (RuntimeException e) {
                log.error("Relay do outbox falhou para o tenant {}: {}", tenantId, e.getClass().getSimpleName(), e);
            }
        }
        return total;
    }

    private int publishBatch(UUID tenantId) {
        Integer n = tx.execute(status -> doPublishBatch(tenantId));
        return n == null ? 0 : n;
    }

    private int doPublishBatch(UUID tenantId) {
        List<Row> rows = jdbc.query(SELECT_BATCH, (rs, i) -> new Row(
                rs.getObject("id", UUID.class), rs.getObject("aggregate_id", UUID.class), rs.getString("event_type"),
                rs.getInt("schema_version"), rs.getString("payload"), rs.getString("headers"),
                rs.getTimestamp("occurred_at").toInstant(), rs.getInt("attempts")),
                tenantId, props.getRelay().getBatchSize());
        if (rows.isEmpty()) {
            return 0;
        }
        if (!breaker.tryAcquirePermission()) {
            return 0;
        }
        long start = System.nanoTime();
        List<Row> sent = new ArrayList<>();
        List<CompletableFuture<SendResult<String, String>>> futures = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();

        for (Row row : rows) {
            try {
                futures.add(sendInOriginTrace(tenantId, row));
                sent.add(row);
            } catch (RuntimeException e) {
                // broker/metadata indisponivel: nao insiste nas demais; elas ficam intactas para o proximo ciclo
                failures.add(new Failure(row, e));
                break;
            }
        }
        List<UUID> okIds = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            Row row = sent.get(i);
            try {
                futures.get(i).get(props.getRelay().getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                okIds.add(row.id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.add(new Failure(row, e));
            } catch (Exception e) {
                failures.add(new Failure(row, e));
            }
        }

        long elapsed = System.nanoTime() - start;
        Timestamp now = Timestamp.from(Instant.now());
        if (!okIds.isEmpty()) {
            jdbc.batchUpdate("UPDATE outbox_events SET published_at = ?, attempts = attempts + 1, last_error = NULL WHERE id = ?",
                    okIds.stream().map(id -> new Object[]{now, id}).toList());
            published.increment(okIds.size());
        }
        for (Failure f : failures) {
            failed.increment();
            int attempts = f.row().attempts() + 1;
            Instant next = Instant.now().plus(backoff(attempts));
            jdbc.update("UPDATE outbox_events SET attempts = ?, last_error = ?, next_attempt_at = ? WHERE id = ?",
                    attempts, describe(f.error()), Timestamp.from(next), f.row().id());
            log.warn("Falha ao publicar o evento {} (tentativa {}); proxima em {}: {}", f.row().id(), attempts, next,
                    f.error().getClass().getSimpleName());
        }
        if (failures.isEmpty()) {
            breaker.onSuccess(elapsed, TimeUnit.NANOSECONDS);
            return okIds.size();
        }
        breaker.onError(elapsed, TimeUnit.NANOSECONDS, failures.get(0).error());
        return 0; // em falha, nao repete o laco no mesmo ciclo
    }

    /**
     * Envia dentro de um span {@code outbox.relay} filho do contexto W3C gravado no outbox: o span do producer Kafka
     * (e portanto o do consumidor) entra no MESMO trace do request HTTP que gerou o evento, mesmo o envio acontecendo
     * em outra thread/instancia, depois do commit.
     */
    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, String>> sendInOriginTrace(UUID tenantId, Row row) {
        if (tracer == null || propagator == null) {
            return kafka.send(toRecord(tenantId, row));
        }
        Map<String, Object> headers = row.headers() == null ? Map.of() : mapper.readValue(row.headers(), Map.class);
        Span span = propagator.<Map<String, Object>>extract(headers,
                (carrier, key) -> carrier.get(key) instanceof String s ? s : null)
                .name("outbox.relay")
                .tag("tenant.id", tenantId.toString())
                .tag("event.type", row.eventType())
                .start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return kafka.send(toRecord(tenantId, row));
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> toRecord(UUID tenantId, Row row) {
        DomainEventType type = DomainEventType.fromType(row.eventType())
                .orElseThrow(() -> new IllegalStateException("Tipo de evento sem rota: " + row.eventType()));
        Map<String, Object> headers = row.headers() == null ? Map.of() : mapper.readValue(row.headers(), Map.class);
        String traceId = headers.get(DomainEventPublisher.HEADER_TRACE) instanceof String s ? s : null;
        Map<String, Object> data = mapper.readValue(row.payload(), Map.class);
        EventEnvelope envelope = new EventEnvelope(row.id(), row.eventType(), props.getSource(), tenantId, traceId,
                row.occurredAt(), row.schemaVersion(), EventEnvelope.JSON, data);
        ProducerRecord<String, String> record = new ProducerRecord<>(type.topic(), row.aggregateId().toString(),
                mapper.writeValueAsString(envelope));
        header(record, DomainEventPublisher.HEADER_TENANT, tenantId.toString());
        header(record, DomainEventPublisher.HEADER_EVENT_TYPE, row.eventType());
        header(record, "eventId", row.id().toString());
        header(record, "schemaVersion", String.valueOf(row.schemaVersion()));
        if (traceId != null) {
            header(record, DomainEventPublisher.HEADER_TRACE, traceId);
        }
        return record;
    }

    private static void header(ProducerRecord<String, String> r, String name, String value) {
        r.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    /** Backoff exponencial: base * 2^(tentativas-1), limitado a maxBackoff. */
    Duration backoff(int attempts) {
        long base = props.getRelay().getBaseBackoff().toMillis();
        long max = props.getRelay().getMaxBackoff().toMillis();
        long factor = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        return Duration.ofMillis(Math.min(max, base * factor));
    }

    private static String describe(Throwable t) {
        Throwable root = t instanceof ExecutionException && t.getCause() != null ? t.getCause() : t;
        String msg = root.getClass().getSimpleName() + ": " + root.getMessage();
        return msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }

    private record Row(UUID id, UUID aggregateId, String eventType, int schemaVersion, String payload, String headers,
                       Instant occurredAt, int attempts) {
    }

    private record Failure(Row row, Throwable error) {
    }
}
