package br.com.cactus.guard.event.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuracao dos eventos de dominio ({@code cactus.events.*}). {@code enabled=false} (perfil test/H2): o publisher
 * vira noop e nada de Kafka e instanciado.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cactus.events")
public class EventProperties {

    /** Chave mestra: outbox + relay + topicos + consumidores. */
    private boolean enabled = false;
    /** Campo {@code source} do envelope. */
    private String source = "cactusguard";

    private final Relay relay = new Relay();
    private final Cleanup cleanup = new Cleanup();
    private final Topics topics = new Topics();
    private final Consumer consumer = new Consumer();

    @Getter
    @Setter
    public static class Relay {
        /** Desligue em instancias que so atendem a API. */
        private boolean enabled = true;
        private Duration pollInterval = Duration.ofMillis(500);
        private int batchSize = 100;
        /** Espera maxima pela confirmacao (acks=all) de cada envio. */
        private Duration sendTimeout = Duration.ofSeconds(10);
        private Duration baseBackoff = Duration.ofSeconds(1);
        private Duration maxBackoff = Duration.ofMinutes(5);
        private Duration metricsInterval = Duration.ofSeconds(10);
    }

    @Getter
    @Setter
    public static class Cleanup {
        /** Dias que o evento publicado fica no outbox (nao e dado regulatorio). */
        private int retentionDays = 14;
        /** Dedup dos consumidores: deve superar a retencao dos topicos (redelivery). */
        private int processedRetentionDays = 30;
        private String cron = "0 30 3 * * *";
    }

    @Getter
    @Setter
    public static class Topics {
        /** false quando os topicos sao gerenciados fora da aplicacao (producao com IaC). */
        private boolean create = true;
        private int partitions = 6;
        private short replicationFactor = 3;
        private Duration retention = Duration.ofDays(14);
        /** DLQ guarda por mais tempo: nunca descartar mensagem. */
        private Duration dlqRetention = Duration.ofDays(90);
    }

    @Getter
    @Setter
    public static class Consumer {
        private boolean enabled = true;
        private String groupId = "cactus-monitoring";
        /** Retentativas bloqueantes antes da DLQ. */
        private int maxRetries = 4;
        private Duration initialBackoff = Duration.ofMillis(500);
        private double backoffMultiplier = 2.0;
        private Duration maxBackoff = Duration.ofSeconds(10);
        private int concurrency = 3;
    }
}
