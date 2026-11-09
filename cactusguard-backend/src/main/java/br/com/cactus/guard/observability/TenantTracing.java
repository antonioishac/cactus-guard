package br.com.cactus.guard.observability;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ponte estatica entre o {@code TenantContext} (sem beans) e o tracing: toda vez que o tenant da thread muda, o span
 * corrente recebe o atributo {@code tenant.id} (UUID, nunca PII). Instalada por {@link TenantSpanConfig}. Sem tracing,
 * o hook e nulo e nada acontece.
 */
public final class TenantTracing {

    public static final String ATTRIBUTE = "tenant.id";

    private static volatile Consumer<UUID> hook;

    private TenantTracing() {
    }

    static void install(Consumer<UUID> consumer) {
        hook = consumer;
    }

    public static void onTenant(UUID tenantId) {
        Consumer<UUID> h = hook;
        if (h != null && tenantId != null) {
            try {
                h.accept(tenantId);
            } catch (RuntimeException ignored) {
                // observabilidade nunca derruba o fluxo de negocio
            }
        }
    }
}
