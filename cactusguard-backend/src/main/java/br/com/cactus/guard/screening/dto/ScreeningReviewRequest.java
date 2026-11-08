package br.com.cactus.guard.screening.dto;

import jakarta.validation.constraints.NotBlank;

/** Revisao humana do hit: CONFIRMED ou FALSE_POSITIVE, com justificativa (minimo 20 caracteres nao brancos). */
public record ScreeningReviewRequest(@NotBlank String status, String justification) {
}
