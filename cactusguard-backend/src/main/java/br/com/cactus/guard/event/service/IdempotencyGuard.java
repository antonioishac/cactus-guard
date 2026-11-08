package br.com.cactus.guard.event.service;

import br.com.cactus.guard.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotencia de consumidor: registra (event_id, consumer) em {@code processed_events} na MESMA transacao do efeito.
 * Se o efeito falhar, o registro faz rollback junto (a reentrega processa de novo); se ja existir, o evento e
 * duplicata e o efeito nao deve ser repetido. Concorrencia: o INSERT concorrente espera o primeiro commit.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private final JdbcTemplate jdbc;

    /** @return true se e a primeira vez (execute o efeito); false se ja foi processado por este consumidor. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(UUID eventId, String consumer) {
        int inserted = jdbc.update(
                "INSERT INTO processed_events (event_id, consumer, tenant_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                eventId, consumer, TenantContext.require());
        return inserted == 1;
    }
}
