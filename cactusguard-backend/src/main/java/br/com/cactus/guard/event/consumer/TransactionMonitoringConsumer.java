package br.com.cactus.guard.event.consumer;

import br.com.cactus.guard.event.config.EventTopics;
import br.com.cactus.guard.event.dto.DomainEventType;
import br.com.cactus.guard.event.dto.EventEnvelope;
import br.com.cactus.guard.event.service.DomainEventPublisher;
import br.com.cactus.guard.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Monitoramento quase em tempo real: para cada {@code cactus.transaction.received}, roda as regras da parte no tenant
 * do evento. Falhas: retentativas com backoff exponencial e, esgotadas (ou erro nao recuperavel), DLQ + registro em
 * dead_letter_events (ver {@code KafkaEventsConfig}). O batch de 6h continua existindo para janelas longas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cactus.events", name = "enabled", havingValue = "true")
public class TransactionMonitoringConsumer {

    private final TransactionMonitoringHandler handler;
    private final ObjectMapper mapper;

    @KafkaListener(id = "cactus-monitoring-transactions", topics = EventTopics.TRANSACTIONS,
            groupId = "${cactus.events.consumer.group-id:cactus-monitoring}",
            concurrency = "${cactus.events.consumer.concurrency:3}",
            autoStartup = "${cactus.events.consumer.enabled:true}")
    public void onMessage(ConsumerRecord<String, String> record) {
        String traceId = header(record, DomainEventPublisher.HEADER_TRACE);
        try {
            if (traceId != null) {
                MDC.put("traceId", traceId);
            }
            UUID headerTenant = parseUuid(header(record, DomainEventPublisher.HEADER_TENANT), "cabecalho tenantId");
            EventEnvelope envelope = parse(record.value());
            if (envelope.id() == null || envelope.tenantId() == null || envelope.data() == null) {
                throw new NonRetryableEventException("Envelope incompleto (id/tenantId/data)");
            }
            // isolamento: o tenant do corpo precisa bater com o do cabecalho (nunca confiar so no corpo)
            if (!envelope.tenantId().equals(headerTenant)) {
                throw new NonRetryableEventException("tenantId do envelope difere do cabecalho");
            }
            if (!DomainEventType.TRANSACTION_RECEIVED.type().equals(envelope.type())) {
                log.debug("Evento {} do tipo {} nao e tratado por este consumidor", envelope.id(), envelope.type());
                return;
            }
            UUID transactionId = parseUuid(String.valueOf(envelope.data().get("transactionId")), "transactionId");
            TenantContext.runAs(headerTenant, () -> handler.handle(envelope.id(), transactionId));
        } finally {
            MDC.remove("traceId");
        }
    }

    private EventEnvelope parse(String json) {
        if (json == null) {
            throw new NonRetryableEventException("Mensagem sem corpo");
        }
        try {
            return mapper.readValue(json, EventEnvelope.class);
        } catch (RuntimeException e) {
            // nao propaga a mensagem do parser: pode ecoar trecho do payload
            throw new NonRetryableEventException("Payload do evento invalido (JSON/estrutura)");
        }
    }

    private static UUID parseUuid(String value, String what) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException e) {
            throw new NonRetryableEventException("Valor invalido em " + what);
        }
    }

    static String header(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null || h.value() == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
