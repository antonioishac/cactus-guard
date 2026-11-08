package br.com.cactus.guard.tenant.dto;

/**
 * Resposta do provisionamento. {@code temporaryPassword} aparece UMA unica vez (nao e recuperavel depois):
 * o administrador do tenant deve troca-la no primeiro acesso.
 */
public record TenantProvisionedResponse(
        TenantResponse tenant,
        String adminUsername,
        String temporaryPassword,
        int rulesCopied) {
}
