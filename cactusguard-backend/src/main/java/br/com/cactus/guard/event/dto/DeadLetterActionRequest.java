package br.com.cactus.guard.event.dto;

import jakarta.validation.constraints.Size;

/**
 * Corpo de reprocessar/descartar. Descartar EXIGE justificativa (minimo de 20 caracteres nao brancos, validado no
 * servico); reprocessar aceita uma observacao opcional que e registrada.
 */
public record DeadLetterActionRequest(@Size(max = 2000) String justification) {
}
