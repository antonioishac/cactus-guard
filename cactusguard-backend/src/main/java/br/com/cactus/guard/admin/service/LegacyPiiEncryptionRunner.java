package br.com.cactus.guard.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Na inicializacao, cifra documentos de UBO/contraparte gravados em claro antes da V30 (a chave
 * AES nao existe no banco, entao nao e possivel em SQL). Idempotente e tolerante a falhas.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class LegacyPiiEncryptionRunner implements ApplicationRunner {

    private final DocumentRehashService rehashService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            rehashService.encryptLegacyPlaintext();
        } catch (RuntimeException e) {
            log.warn("Cifragem de PII legada nao concluida; use POST /api/v1/admin/rehash-documents ({})",
                    e.getClass().getSimpleName());
        }
    }
}
