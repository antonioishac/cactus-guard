package br.com.cactus.guard.metrics;

import br.com.cactus.guard.tenant.TenantContext;
import io.micrometer.core.instrument.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tag {@code tenant} das metricas de negocio com cardinalidade CONTROLADA (spec 038):
 * <ul>
 *   <li>desligada por padrao ({@code cactus.metrics.tenant-tag.enabled=false}): metricas agregadas da plataforma;
 *       o detalhe por tenant esta nos spans ({@code tenant.id}) e nos logs;</li>
 *   <li>ligada: no maximo {@code max-tenants} valores distintos por processo (padrao 50); do (N+1)-esimo tenant em
 *       diante o valor e {@code other}. O valor e o UUID do tenant (nunca nome/CNPJ).</li>
 * </ul>
 */
@Component
public class MetricTags {

    public static final String OTHER = "other";

    private final boolean enabled;
    private final int maxTenants;
    private final Set<UUID> known = ConcurrentHashMap.newKeySet();

    public MetricTags(@Value("${cactus.metrics.tenant-tag.enabled:false}") boolean enabled,
                      @Value("${cactus.metrics.tenant-tag.max-tenants:50}") int maxTenants) {
        this.enabled = enabled;
        this.maxTenants = maxTenants;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Tag do tenant corrente (vazia quando desligado ou sem tenant). */
    public Iterable<Tag> tenant() {
        return TenantContext.current().map(this::tenant).orElse(java.util.List.of());
    }

    public Iterable<Tag> tenant(UUID tenantId) {
        if (!enabled || tenantId == null) {
            return java.util.List.of();
        }
        return java.util.List.of(Tag.of("tenant", value(tenantId)));
    }

    String value(UUID tenantId) {
        if (known.contains(tenantId)) {
            return tenantId.toString();
        }
        synchronized (known) {
            if (known.size() < maxTenants) {
                known.add(tenantId);
                return tenantId.toString();
            }
        }
        return known.contains(tenantId) ? tenantId.toString() : OTHER;
    }
}
