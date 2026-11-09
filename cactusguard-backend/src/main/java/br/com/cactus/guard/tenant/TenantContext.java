package br.com.cactus.guard.tenant;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tenant da execucao corrente (ThreadLocal). Quem le: {@link TenantIdentifierResolver} (Hibernate) e
 * {@link TenantAwareDataSource} (GUC {@code app.tenant_id} para o RLS). Quem escreve: filtro JWT,
 * {@link TenantRunner} (jobs), {@link TenantTaskDecorator} (@Async) e listeners de evento.
 * <p>
 * Fail-closed: sem tenant, {@link #currentOrNone()} devolve {@link #NO_TENANT} (UUID zero), que nao
 * casa com nenhuma linha (filtro Hibernate e RLS) e viola a FK de tenants em qualquer INSERT.
 * O fallback para o tenant default existe SOMENTE quando {@code cactus.tenant.fallback-id} esta
 * configurado (perfil de teste); em dev/prod nao ha fallback.
 */
public final class TenantContext {

    /** Tenant "default": recebe todos os dados anteriores a multi-tenancy (V33). */
    public static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    /** Tenant que abriga apenas os usuarios PLATFORM_ADMIN (nenhum dado de negocio). */
    public static final UUID PLATFORM_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    /** Sentinela "sem tenant": nunca existe em tenants. */
    public static final UUID NO_TENANT = new UUID(0L, 0L);

    public static final String MDC_KEY = "tenantId";

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    private static volatile UUID fallback;

    private TenantContext() {
    }

    /** Somente testes: tenant usado quando nenhum foi definido na thread. */
    static void configureFallback(UUID tenantId) {
        fallback = tenantId;
    }

    /** Tenant explicito da thread (sem fallback). */
    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Tenant efetivo ou {@link #NO_TENANT}; usado pelo Hibernate e pelo RLS. */
    public static UUID currentOrNone() {
        UUID t = CURRENT.get();
        if (t != null) {
            return t;
        }
        UUID fb = fallback;
        return fb != null ? fb : NO_TENANT;
    }

    /** Tenant efetivo ou excecao (codigo que so faz sentido dentro de um tenant). */
    public static UUID require() {
        UUID t = currentOrNone();
        if (NO_TENANT.equals(t)) {
            throw new IllegalStateException("Operacao exige um tenant no contexto");
        }
        return t;
    }

    public static void set(UUID tenantId) {
        if (tenantId == null) {
            clear();
            return;
        }
        CURRENT.set(tenantId);
        MDC.put(MDC_KEY, tenantId.toString());
        br.com.cactus.guard.observability.TenantTracing.onTenant(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_KEY);
    }

    /**
     * Executa no tenant informado e restaura o anterior. Trocar de tenant com transacao ativa e proibido:
     * a sessao Hibernate e a conexao (GUC do RLS) ja estao presas ao tenant em que a transacao comecou.
     */
    public static <T> T callAs(UUID tenantId, Supplier<T> action) {
        UUID previous = CURRENT.get();
        if (!tenantId.equals(currentOrNone()) && TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Troca de tenant dentro de transacao ativa nao e permitida");
        }
        set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }

    public static void runAs(UUID tenantId, Runnable action) {
        callAs(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
