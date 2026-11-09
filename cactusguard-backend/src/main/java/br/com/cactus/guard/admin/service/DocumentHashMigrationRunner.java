package br.com.cactus.guard.admin.service;

import br.com.cactus.guard.tenant.TenantContext;
import br.com.cactus.guard.tenant.entity.Tenant;
import br.com.cactus.guard.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Migracao unica, na inicializacao, dos hashes de documento do pepper global (v1) para o pepper por tenant (v2).
 * <p>
 * A versao fica em {@code tenants.hash_version} (V44): tenants com versao &lt; {@value #CURRENT_VERSION} sao
 * reprocessados dentro do proprio contexto de tenant (o rehash decifra o documento e recalcula
 * {@code document_hash} de parties, UBOs e contrapartes) e so entao marcados como v2. Falha em um tenant nao
 * impede os demais e deixa o tenant em v1 (nova tentativa na proxima subida ou via
 * {@code POST /api/v1/admin/rehash-documents}). Idempotente: tenants v2 nao sao tocados. Tenants SUSPENDED tambem
 * migram (o tenant 'platform' nao tem dados de negocio e e ignorado).
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DocumentHashMigrationRunner implements ApplicationRunner {

    static final int CURRENT_VERSION = 2;

    private final TenantRepository tenantRepository;
    private final DocumentRehashService rehashService;
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try {
            migratePending();
        } catch (RuntimeException e) {
            log.warn("Migracao de hash de documento por tenant nao concluida: {}", e.getClass().getSimpleName());
        }
    }

    /** Devolve quantos tenants foram migrados. */
    public int migratePending() {
        int migrated = 0;
        for (Tenant t : tenantRepository.findAll()) {
            UUID id = t.getId();
            if (TenantContext.PLATFORM_TENANT_ID.equals(id) || t.getHashVersion() >= CURRENT_VERSION) {
                continue;
            }
            try {
                TenantContext.runAs(id, rehashService::rehashAll);
                jdbc.update("UPDATE tenants SET hash_version = ? WHERE id = ?", CURRENT_VERSION, id);
                migrated++;
                log.info("Hash de documento migrado para pepper por tenant: tenant={}", t.getCode());
            } catch (RuntimeException e) {
                log.error("Migracao de hash falhou para o tenant {}: {}", t.getCode(), e.getClass().getSimpleName());
            }
        }
        return migrated;
    }
}
