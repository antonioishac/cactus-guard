package br.com.cactus.guard.tenant.service;

import br.com.cactus.guard.tenant.TenantContext;
import br.com.cactus.guard.tenant.entity.Tenant;
import br.com.cactus.guard.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Executa trabalho de plataforma (jobs agendados, startup) tenant a tenant. Cada tenant roda isolado: falha
 * em um nao interrompe os demais e o contexto e restaurado ao fim. Tenants SUSPENDED e o tenant
 * 'platform' (sem dados de negocio) sao ignorados.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantRunner {

    private final TenantRepository tenantRepository;

    public List<UUID> activeTenantIds() {
        return tenantRepository.findByStatusOrderByCodeAsc(Tenant.ACTIVE).stream()
                .map(Tenant::getId)
                .filter(id -> !TenantContext.PLATFORM_TENANT_ID.equals(id))
                .toList();
    }

    /** Roda a acao uma vez por tenant ativo, com o tenant no contexto. Devolve quantos tenants falharam. */
    public int forEachActiveTenant(String jobName, Consumer<UUID> action) {
        int failures = 0;
        for (UUID tenantId : activeTenantIds()) {
            try {
                TenantContext.runAs(tenantId, () -> action.accept(tenantId));
            } catch (RuntimeException e) {
                failures++;
                log.error("Job {} falhou para o tenant {}: {}", jobName, tenantId, e.getClass().getSimpleName(), e);
            }
        }
        return failures;
    }
}
