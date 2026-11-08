package br.com.cactus.guard.event.dto;

import br.com.cactus.guard.event.entity.DeadLetterEvent;

import java.time.Instant;
import java.util.UUID;

/** Mensagem na DLQ. O payload e o envelope do evento (sem PII por contrato). */
public record DeadLetterResponse(UUID id,
                                 UUID eventId,
                                 String sourceTopic,
                                 int sourcePartition,
                                 long sourceOffset,
                                 String consumerGroup,
                                 String key,
                                 String payload,
                                 String reason,
                                 String stackTrace,
                                 String status,
                                 Instant failedAt,
                                 Instant resolvedAt,
                                 String resolvedBy,
                                 String resolutionReason) {

    public static DeadLetterResponse from(DeadLetterEvent d) {
        return new DeadLetterResponse(d.getId(), d.getEventId(), d.getSourceTopic(), d.getSourcePartition(),
                d.getSourceOffset(), d.getConsumerGroup(), d.getRecordKey(), d.getPayload(), d.getReason(),
                d.getStackTrace(), d.getStatus(), d.getFailedAt(), d.getResolvedAt(), d.getResolvedBy(),
                d.getResolutionReason());
    }
}
