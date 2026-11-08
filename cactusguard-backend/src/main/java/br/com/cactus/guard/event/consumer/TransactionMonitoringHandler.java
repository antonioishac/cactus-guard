package br.com.cactus.guard.event.consumer;

import br.com.cactus.guard.event.service.IdempotencyGuard;
import br.com.cactus.guard.rule.engine.RuleEngineService;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Efeito do consumidor de TransactionReceived: roda as regras ativas da parte (alertas em segundos). A marca de
 * idempotencia e o efeito (alertas + eventos AlertCreated no outbox) ficam na MESMA transacao: exatamente um efeito
 * por evento, mesmo com entrega duplicada. Executa dentro do tenant do evento (RLS + filtro Hibernate).
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "cactus.events", name = "enabled", havingValue = "true")
public class TransactionMonitoringHandler {

    public static final String CONSUMER = "monitoring-transactions";

    private final IdempotencyGuard idempotency;
    private final RuleEngineService ruleEngine;
    private final TransactionRepository transactionRepository;
    private final Counter processed;
    private final Counter duplicates;

    public TransactionMonitoringHandler(IdempotencyGuard idempotency, RuleEngineService ruleEngine,
                                        TransactionRepository transactionRepository, MeterRegistry meters) {
        this.idempotency = idempotency;
        this.ruleEngine = ruleEngine;
        this.transactionRepository = transactionRepository;
        this.processed = Counter.builder("cactus.events.consumed").tag("consumer", CONSUMER).register(meters);
        this.duplicates = Counter.builder("cactus.events.duplicates").tag("consumer", CONSUMER).register(meters);
    }

    /** @return true se processou; false se era duplicata. */
    @Transactional
    public boolean handle(UUID eventId, UUID transactionId) {
        if (!idempotency.firstTime(eventId, CONSUMER)) {
            duplicates.increment();
            log.info("Evento {} ja processado por {}: ignorado (duplicata)", eventId, CONSUMER);
            return false;
        }
        // Agregado inexistente NESTE tenant (inclui evento de outro tenant com cabecalho trocado): retentar nao adianta
        if (!transactionRepository.existsById(transactionId)) {
            throw new NonRetryableEventException("Transacao do evento inexistente no tenant: " + transactionId);
        }
        int alerts = ruleEngine.evaluateForTransaction(transactionId);
        processed.increment();
        log.info("Evento {} processado: transacao {} gerou {} alerta(s)", eventId, transactionId, alerts);
        return true;
    }
}
