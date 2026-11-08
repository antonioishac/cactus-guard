package br.com.cactus.guard.tenant.dto;

import jakarta.validation.constraints.NotBlank;

/** Justificativa obrigatoria para suspender/reativar um tenant. */
public record TenantStatusRequest(@NotBlank String reason) {
}
