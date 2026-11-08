package br.com.cactus.guard.case_.dto;

import jakarta.validation.constraints.NotBlank;

/** Rejeicao da aprovacao (quatro olhos): justificativa obrigatoria. */
public record CaseRejectApprovalRequest(@NotBlank String justification) {
}
