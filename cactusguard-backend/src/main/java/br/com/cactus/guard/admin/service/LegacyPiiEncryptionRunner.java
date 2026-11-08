package br.com.cactus.guard.admin.service;

import br.com.cactus.guard.tenant.service.TenantRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Na inicializacao, cifra documentos de UBO/contraparte gravados em claro antes da V30 (a chave
 * AES nao existe no banco, entao nao e possivel em SQL). Idempotente e tolerante a falhas.
 * Roda tenant a tenant (a tabela esta sob RLS; sem tenant no contexto nada seria visivel).
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class LegacyPiiEncryptionRunner implements ApplicationRunner {

    private final DocumentRehashService rehashService;
    private final TenantRunner tenantRunner;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int failures = tenantRunner.forEachActiveTenant("LegacyPiiEncryption", t -> rehashService.encryptLegacyPlaintext());
            if (failures > 0) {
                log.warn("Cifragem de PII legada falhou em {} tenant(s); use POST /api/v1/admin/rehash-documents", failures);
            }
        } catch (RuntimeException e) {
            log.warn("Cifragem de PII legada nao concluida; use POST /api/v1/admin/rehash-documents ({})",
                    e.getClass().getSimpleName());
        }
    }
}
