package br.com.cactus.guard.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Entrega ao Hibernate o tenant da sessao (lido quando o EntityManager abre, i.e. no inicio da
 * transacao). Sem tenant devolve {@link TenantContext#NO_TENANT}: o filtro nao casa nada (fail-closed).
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.currentOrNone();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
