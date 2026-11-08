package br.com.cactus.guard.common.event;

import java.util.UUID;

/** Publicado (apos criar o alerta) quando um alerta de severidade CRITICAL e persistido. Sem dados do cliente. Leva o tenant: o listener assincrono roda nele. */
public record CriticalAlertCreatedEvent(UUID alertId, UUID tenantId) {
}
