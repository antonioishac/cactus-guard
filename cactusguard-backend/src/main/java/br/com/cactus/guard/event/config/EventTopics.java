package br.com.cactus.guard.event.config;

import java.util.List;

/**
 * Nomes dos topicos (contrato entre produtores e consumidores; sufixo .v1 = versao do contrato). Chave de toda
 * mensagem = aggregate_id (ordem por agregado). Cada topico tem o par {@code <topico>.dlq}.
 * <p>
 * Decisao (spec 047): nao ha topicos {@code .retry}. As retentativas sao bloqueantes, com backoff exponencial, no
 * proprio consumidor (DefaultErrorHandler), o que preserva a ordem por agregado; esgotadas, a mensagem vai a
 * {@code .dlq} e e registrada em dead_letter_events.
 */
public final class EventTopics {

    public static final String TRANSACTIONS = "cactus.transactions.v1";
    public static final String ALERTS = "cactus.alerts.v1";
    public static final String CASES = "cactus.cases.v1";
    public static final String PARTIES = "cactus.parties.v1";
    public static final String COMMUNICATIONS = "cactus.communications.v1";
    public static final String AUDIT = "cactus.audit.v1";

    public static final String DLQ_SUFFIX = ".dlq";

    public static final List<String> ALL = List.of(TRANSACTIONS, ALERTS, CASES, PARTIES, COMMUNICATIONS, AUDIT);

    private EventTopics() {
    }

    public static String dlq(String topic) {
        return topic + DLQ_SUFFIX;
    }
}
