package br.com.cactus.guard.event.service;

import br.com.cactus.guard.event.config.EventProperties;
import br.com.cactus.guard.event.repository.OutboxEventRepository;
import br.com.cactus.guard.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Expurgo do outbox (publicados) e da tabela de idempotencia. Nao sao dados regulatorios: o registro oficial esta
 * nas tabelas de negocio e no audit_log (retencao 10 anos). dead_letter_events NUNCA e expurgada aqui.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventsCleanupService {

    private final OutboxEventRepository outboxRepository;
    private final JdbcTemplate jdbc;
    private final EventProperties props;

    /** Roda no tenant corrente. */
    @Transactional
    public int cleanupCurrentTenant() {
        Instant outboxCut = Instant.now().minus(Duration.ofDays(props.getCleanup().getRetentionDays()));
        Instant processedCut = Instant.now().minus(Duration.ofDays(props.getCleanup().getProcessedRetentionDays()));
        int outbox = outboxRepository.deletePublishedBefore(outboxCut);
        int processed = jdbc.update("DELETE FROM processed_events WHERE tenant_id = ? AND processed_at < ?",
                TenantContext.require(), Timestamp.from(processedCut));
        log.info("Expurgo de eventos: {} publicados do outbox e {} registros de idempotencia", outbox, processed);
        return outbox + processed;
    }
}
