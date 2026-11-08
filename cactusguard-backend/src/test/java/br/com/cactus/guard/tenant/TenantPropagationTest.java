package br.com.cactus.guard.tenant;

import br.com.cactus.guard.common.event.CriticalAlertCreatedEvent;
import br.com.cactus.guard.device.service.PushDispatcher;
import br.com.cactus.guard.rule.dto.MonitoringRuleResponse;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.rule.service.MonitoringRuleService;
import br.com.cactus.guard.tenant.entity.Tenant;
import br.com.cactus.guard.tenant.repository.TenantRepository;
import br.com.cactus.guard.tenant.service.TenantRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Propagacao do tenant fora da thread da requisicao: {@code @Async}, listener de evento, jobs (TenantRunner) e
 * isolamento do cache. (O isolamento de dados no banco e provado em {@link TenantIsolationIT}.)
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TenantPropagationTest.AsyncProbe.class)
class TenantPropagationTest {

    /** Sonda: devolve o tenant visto na thread do executor assincrono. */
    @Component
    static class AsyncProbe {
        @Async
        public CompletableFuture<Optional<UUID>> tenantSeenByAsyncThread() {
            return CompletableFuture.completedFuture(TenantContext.current());
        }
    }

    @Autowired private AsyncProbe probe;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantRunner tenantRunner;
    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private MonitoringRuleService ruleService;
    @Autowired private MonitoringRuleRepository ruleRepository;
    @Autowired private CacheManager cacheManager;
    @MockitoBean private PushDispatcher dispatcher;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private UUID tenant(String status) {
        UUID id = UUID.randomUUID();
        tenantRepository.save(Tenant.builder().id(id).code("p-" + id.toString().substring(0, 8))
                .name("prop").regulatorySegment("OTHER").status(status).build());
        return id;
    }

    @Test
    void asyncTaskInheritsTheSubmittersTenantAndLeavesNothingBehind() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        CompletableFuture<Optional<UUID>> fa = TenantContext.callAs(a, probe::tenantSeenByAsyncThread);
        CompletableFuture<Optional<UUID>> fb = TenantContext.callAs(b, probe::tenantSeenByAsyncThread);
        assertEquals(Optional.of(a), fa.get(5, TimeUnit.SECONDS));
        assertEquals(Optional.of(b), fb.get(5, TimeUnit.SECONDS));

        // sem tenant na origem, a thread do executor tambem nao pode herdar nada de uso anterior
        TenantContext.clear();
        for (int i = 0; i < 20; i++) {
            assertEquals(Optional.empty(), probe.tenantSeenByAsyncThread().get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void asyncEventListenerRunsInTheTenantCarriedByTheEvent() throws Exception {
        UUID eventTenant = UUID.randomUUID();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Optional<UUID>> seen = new AtomicReference<>();
        doAnswer(inv -> {
            seen.set(TenantContext.current());
            done.countDown();
            return null;
        }).when(dispatcher).dispatch(any(), any(), any(), any(), any());

        // publicador em OUTRO tenant: o listener deve usar o tenant do evento, nao o da origem
        TenantContext.runAs(UUID.randomUUID(),
                () -> publisher.publishEvent(new CriticalAlertCreatedEvent(UUID.randomUUID(), eventTenant)));

        assertTrue(done.await(10, TimeUnit.SECONDS), "listener nao executou");
        assertEquals(Optional.of(eventTenant), seen.get());
    }

    @Test
    void tenantRunnerVisitsOnlyActiveTenantsAndIsolatesFailures() {
        UUID ok1 = tenant(Tenant.ACTIVE);
        UUID ok2 = tenant(Tenant.ACTIVE);
        UUID bad = tenant(Tenant.ACTIVE);
        UUID suspended = tenant(Tenant.SUSPENDED);
        List<UUID> visited = Collections.synchronizedList(new ArrayList<>());
        List<UUID> seenInContext = Collections.synchronizedList(new ArrayList<>());

        int failures = tenantRunner.forEachActiveTenant("teste", id -> {
            if (id.equals(ok1) || id.equals(ok2) || id.equals(bad) || id.equals(suspended)) {
                visited.add(id);
                seenInContext.add(TenantContext.current().orElse(null));
            }
            if (id.equals(bad)) {
                throw new IllegalStateException("falha simulada");
            }
        });

        assertTrue(visited.containsAll(List.of(ok1, ok2, bad)));
        assertFalse(visited.contains(suspended), "tenant suspenso nao pode ser processado por job");
        assertEquals(visited, seenInContext, "o tenant deve estar no contexto durante a execucao");
        assertEquals(1, failures);
        assertTrue(TenantContext.current().isEmpty(), "contexto restaurado ao final");
        assertFalse(tenantRunner.activeTenantIds().contains(TenantContext.PLATFORM_TENANT_ID));
    }

    @Test
    void cacheIsPartitionedPerTenant() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String code = "CACHE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        TenantContext.runAs(a, () -> {
            MonitoringRule r = new MonitoringRule();
            r.setCode(code);
            r.setName("regra do tenant A");
            r.setCategory("TRANSACTIONAL");
            r.setSeverity("LOW");
            r.setActive(true);
            ruleRepository.save(r);
        });

        List<String> seenByA = codes(a);
        assertTrue(seenByA.contains(code));
        // A ja esta em cache; B (mesma chave 'active') nao pode receber a lista de A
        assertFalse(codes(b).contains(code), "cache vazou entre tenants");
        assertTrue(codes(a).contains(code));

        // caches fisicos distintos por tenant
        TenantContext.runAs(a, () -> TenantContext.runAs(a, () -> assertNotNull(cacheManager.getCache("rules"))));
        Object cacheA = TenantContext.callAs(a, () -> cacheManager.getCache("rules"));
        Object cacheB = TenantContext.callAs(b, () -> cacheManager.getCache("rules"));
        assertNotSame(cacheA, cacheB);
    }

    private List<String> codes(UUID tenantId) {
        return TenantContext.callAs(tenantId, () -> ruleService.findAllActive().stream()
                .map(MonitoringRuleResponse::getCode).toList());
    }
}
