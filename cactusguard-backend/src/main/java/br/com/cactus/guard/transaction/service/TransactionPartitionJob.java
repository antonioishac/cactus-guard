package br.com.cactus.guard.transaction.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Criacao antecipada das particoes mensais de {@code transactions} (V48-V50) e vigilancia da particao DEFAULT.
 * Idempotente (a funcao SQL usa advisory lock; varias instancias podem rodar). Roda na subida e diariamente, de modo
 * que uma execucao perdida e recuperada na seguinte. NUNCA remove particoes: retencao minima de 10 anos
 * (Circular BCB 3.978); arquivamento futuro = DETACH PARTITION manual apos 10 anos (specs/047 secao 12).
 * Linhas na DEFAULT significam dados fora de faixa: log ERROR + gauge {@code cactus.transactions.default_partition_rows}.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cactus.partitions", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TransactionPartitionJob {

    private final JdbcTemplate jdbc;
    private final int monthsAhead;
    private final AtomicLong defaultRows = new AtomicLong();

    public TransactionPartitionJob(JdbcTemplate jdbc, MeterRegistry meters,
                                   @Value("${cactus.partitions.months-ahead:3}") int monthsAhead) {
        this.jdbc = jdbc;
        this.monthsAhead = monthsAhead;
        Gauge.builder("cactus.transactions.default_partition_rows", defaultRows, AtomicLong::get).register(meters);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "${cactus.partitions.cron:0 30 2 * * *}")
    public void run() {
        try {
            Integer created = jdbc.queryForObject("SELECT ensure_transaction_partitions(?, 0)", Integer.class, monthsAhead);
            Long outOfRange = jdbc.queryForObject("SELECT transactions_default_partition_rows()", Long.class);
            defaultRows.set(outOfRange == null ? 0 : outOfRange);
            log.info("Particoes de transactions verificadas: {} criada(s), {} linha(s) na DEFAULT", created, outOfRange);
            if (outOfRange != null && outOfRange > 0) {
                log.error("ALERTA: {} transacao(oes) na particao DEFAULT (fora de faixa). Criar a particao do mes e mover as linhas.",
                        outOfRange);
            }
        } catch (RuntimeException e) {
            log.error("Falha ao garantir particoes de transactions (sera repetido na proxima execucao): {}", e.getMessage());
        }
    }
}
