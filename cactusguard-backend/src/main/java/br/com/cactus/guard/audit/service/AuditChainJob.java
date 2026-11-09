package br.com.cactus.guard.audit.service;

import br.com.cactus.guard.audit.dto.AuditIntegrityResponse;
import br.com.cactus.guard.securityevent.entity.SecurityEvent;
import br.com.cactus.guard.securityevent.repository.SecurityEventRepository;
import br.com.cactus.guard.tenant.TenantContext;
import br.com.cactus.guard.tenant.service.TenantRunner;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Jobs da cadeia de auditoria: verificacao diaria (alerta + evento de seguranca + registro na propria trilha se
 * quebrar) e ancoragem periodica da cabeca de cada tenant (tabela + evento {@code cactus.audit.anchor}).
 */
@Slf4j
@Component
public class AuditChainJob {

    private final AuditIntegrityService integrity;
    private final AuditService auditService;
    private final TenantRunner tenantRunner;
    private final SecurityEventRepository securityEvents;
    private final Counter broken;
    private final AtomicInteger tenantsBroken = new AtomicInteger();
    private final boolean enabled;

    public AuditChainJob(AuditIntegrityService integrity, AuditService auditService, TenantRunner tenantRunner,
                         SecurityEventRepository securityEvents, MeterRegistry meters,
                         @Value("${cactus.audit.chain.jobs-enabled:true}") boolean enabled) {
        this.integrity = integrity;
        this.auditService = auditService;
        this.tenantRunner = tenantRunner;
        this.securityEvents = securityEvents;
        this.enabled = enabled;
        this.broken = Counter.builder("cactus.audit.chain.broken")
                .description("Verificacoes da cadeia de auditoria que encontraram divergencia").register(meters);
        Gauge.builder("cactus.audit.chain.tenants.broken", tenantsBroken, AtomicInteger::get)
                .description("Tenants com cadeia de auditoria quebrada na ultima verificacao").register(meters);
    }

    private List<UUID> tenants() {
        List<UUID> ids = new ArrayList<>(tenantRunner.activeTenantIds());
        ids.add(TenantContext.PLATFORM_TENANT_ID); // a auditoria da plataforma tambem e encadeada
        return ids;
    }

    @Scheduled(cron = "${cactus.audit.chain.verify-cron:0 30 3 * * *}")
    public void verifyAll() {
        if (!enabled) {
            return;
        }
        int bad = 0;
        for (UUID tenantId : tenants()) {
            try {
                AuditIntegrityResponse r = TenantContext.callAs(tenantId, integrity::verify);
                if (!r.ok()) {
                    bad++;
                    onBroken(tenantId, r);
                }
            } catch (RuntimeException e) {
                log.error("Falha ao verificar a cadeia de auditoria do tenant {}: {}", tenantId, e.getClass().getSimpleName(), e);
            }
        }
        tenantsBroken.set(bad);
    }

    private void onBroken(UUID tenantId, AuditIntegrityResponse r) {
        broken.increment();
        log.error("ALERTA DE SEGURANCA: cadeia de auditoria QUEBRADA no tenant {} (motivo={}, seq={})", tenantId,
                r.firstDivergence().reason(), r.firstDivergence().chainSeq());
        securityEvents.save(SecurityEvent.builder()
                .eventType(SecurityEvent.AUDIT_CHAIN_BROKEN)
                .result(SecurityEvent.RESULT_FAILURE)
                .tenantId(tenantId)
                .build());
        try {
            // registra na propria trilha (proxima linha da cadeia) - interno, nunca exposto ao cliente final
            TenantContext.runAs(tenantId, () -> auditService.log("AUDIT_CHAIN_BROKEN", "AUDIT_CHAIN", null, null,
                    Map.of("reason", r.firstDivergence().reason(),
                            "chainSeq", String.valueOf(r.firstDivergence().chainSeq()))));
        } catch (RuntimeException e) {
            log.error("Nao foi possivel registrar AUDIT_CHAIN_BROKEN na trilha do tenant {}", tenantId, e);
        }
    }

    @Scheduled(cron = "${cactus.audit.chain.anchor-cron:0 5 * * * *}")
    public void anchorAll() {
        if (!enabled) {
            return;
        }
        for (UUID tenantId : tenants()) {
            try {
                TenantContext.runAs(tenantId, integrity::anchor);
            } catch (RuntimeException e) {
                log.error("Falha ao ancorar a cadeia de auditoria do tenant {}: {}", tenantId, e.getClass().getSimpleName(), e);
            }
        }
    }
}
