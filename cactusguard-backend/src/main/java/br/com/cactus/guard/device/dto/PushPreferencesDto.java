package br.com.cactus.guard.device.dto;

import jakarta.validation.constraints.NotNull;

/** criticalAlerts: alertas criticos e aprovacoes pendentes; deadlines: prazos regulatorios (SLA). */
public record PushPreferencesDto(@NotNull Boolean criticalAlerts, @NotNull Boolean deadlines) {
}
