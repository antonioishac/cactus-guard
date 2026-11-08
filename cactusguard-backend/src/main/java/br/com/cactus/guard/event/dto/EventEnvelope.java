package br.com.cactus.guard.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope no estilo CloudEvents publicado no Kafka. {@code data} NAO contem PII: apenas ids e campos nao
 * pessoais; o consumidor busca o restante no banco, autenticado e dentro do tenant.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope(UUID id,
                            String type,
                            String source,
                            UUID tenantId,
                            String traceId,
                            Instant occurredAt,
                            int schemaVersion,
                            String dataContentType,
                            Map<String, Object> data) {

    public static final String JSON = "application/json";
}
