package br.com.cactus.guard.case_.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Abertura de case a partir de um alerta (selecao): justificativa obrigatoria. */
public record CaseCreateRequest(@NotNull UUID alertId, @NotBlank String justification) {
}
