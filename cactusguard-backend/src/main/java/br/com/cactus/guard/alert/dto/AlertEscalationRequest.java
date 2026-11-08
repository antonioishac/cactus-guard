package br.com.cactus.guard.alert.dto;

import jakarta.validation.constraints.NotBlank;

public record AlertEscalationRequest(@NotBlank String justification) {
}
