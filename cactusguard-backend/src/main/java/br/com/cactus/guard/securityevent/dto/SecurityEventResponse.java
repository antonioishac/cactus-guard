package br.com.cactus.guard.securityevent.dto;

import br.com.cactus.guard.securityevent.entity.SecurityEvent;

import java.time.Instant;
import java.util.UUID;

/** Visao de plataforma de um evento de seguranca: username sempre mascarado; sem hash e sem senha. */
public record SecurityEventResponse(
        UUID id,
        String eventType,
        String result,
        String usernameMasked,
        UUID tenantId,
        String ipAddress,
        String userAgent,
        Instant occurredAt) {

    public static SecurityEventResponse from(SecurityEvent e) {
        return new SecurityEventResponse(e.getId(), e.getEventType(), e.getResult(), e.getUsernameMasked(),
                e.getTenantId(), e.getIpAddress(), e.getUserAgent(), e.getOccurredAt());
    }
}
