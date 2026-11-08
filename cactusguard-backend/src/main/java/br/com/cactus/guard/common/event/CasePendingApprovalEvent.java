package br.com.cactus.guard.common.event;

import java.util.UUID;

/**
 * Publicado quando um case entra em PENDING_APPROVAL (aguarda segunda aprovacao).
 * {@code decidedById} e excluido dos destinatarios (quatro olhos); pode ser nulo.
 */
public record CasePendingApprovalEvent(UUID caseId, UUID decidedById, UUID tenantId) {
}
