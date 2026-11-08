package br.com.cactus.guard.case_.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CaseEscalationRequest(@NotBlank String justification, UUID escalatedToId) {
}
