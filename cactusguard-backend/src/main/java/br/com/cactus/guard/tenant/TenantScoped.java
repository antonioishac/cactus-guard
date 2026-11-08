package br.com.cactus.guard.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

/**
 * Superclasse de toda entity de dado de cliente. O Hibernate preenche {@code tenant_id} no INSERT a
 * partir do {@link TenantContext} e injeta {@code tenant_id = ?} em toda consulta (HQL, Criteria,
 * find por id, DML em lote). Nunca escreva {@code WHERE tenant_id} manual (spec 047); o PostgreSQL
 * repete a regra via Row Level Security (V38).
 */
@MappedSuperclass
public abstract class TenantScoped {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    public UUID getTenantId() {
        return tenantId;
    }
}
