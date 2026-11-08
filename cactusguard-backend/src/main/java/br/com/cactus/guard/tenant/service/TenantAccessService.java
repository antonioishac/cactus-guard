package br.com.cactus.guard.tenant.service;

import br.com.cactus.guard.tenant.entity.Tenant;
import br.com.cactus.guard.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pergunta "o tenant existe e esta ACTIVE?" a cada requisicao, com cache local de TTL curto (30 s) para nao
 * onerar o banco. Suspender/reativar invalida localmente na hora; em varias instancias a suspensao vale em ate 30 s.
 */
@Service
@RequiredArgsConstructor
public class TenantAccessService {

    static final Duration TTL = Duration.ofSeconds(30);

    private record Entry(boolean active, long expiresAtNanos) { }

    private final TenantRepository tenantRepository;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public boolean isActive(UUID tenantId) {
        long now = System.nanoTime();
        Entry e = cache.get(tenantId);
        if (e != null && e.expiresAtNanos() > now) {
            return e.active();
        }
        boolean active = tenantRepository.findById(tenantId).map(Tenant::isActive).orElse(false);
        cache.put(tenantId, new Entry(active, now + TTL.toNanos()));
        return active;
    }

    public void invalidate(UUID tenantId) {
        cache.remove(tenantId);
    }
}
