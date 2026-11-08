package br.com.cactus.guard.tenant;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.UUID;

/**
 * Isola o cache por tenant: o cache logico "rules" do tenant X e o cache fisico "X:rules" do delegate
 * (Redis: chaves {@code X:rules::<key>}; {@code allEntries} limpa so o tenant). Sem tenant usa o
 * namespace "none", que nenhum tenant real alcanca.
 */
public class TenantAwareCacheManager implements CacheManager {

    private final CacheManager delegate;

    public TenantAwareCacheManager(CacheManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public Cache getCache(String name) {
        UUID tenant = TenantContext.currentOrNone();
        String ns = TenantContext.NO_TENANT.equals(tenant) ? "none" : tenant.toString();
        return delegate.getCache(ns + ":" + name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    public CacheManager getDelegate() {
        return delegate;
    }
}
