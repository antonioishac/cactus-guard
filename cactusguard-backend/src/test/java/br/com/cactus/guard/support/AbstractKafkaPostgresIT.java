package br.com.cactus.guard.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;

import java.util.UUID;

/**
 * Base dos ITs de eventos: PostgreSQL real (papel sem bypass de RLS, schema so por Flyway) + Kafka real
 * (Testcontainers, KRaft de 1 no). Liga {@code cactus.events.enabled} (o pai desliga) com topicos de RF 1 / 3 particoes
 * e tempos curtos de backoff para os testes terminarem rapido.
 */
public abstract class AbstractKafkaPostgresIT extends AbstractPostgresIT {

    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    private static final boolean DOCKER = DockerClientFactory.instance().isDockerAvailable();

    static {
        if (DOCKER) {
            KAFKA.start();
        }
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry r) {
        if (!DOCKER) {
            return;
        }
        r.add("cactus.events.enabled", () -> "true");
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("cactus.events.topics.partitions", () -> "3");
        r.add("cactus.events.topics.replication-factor", () -> "1");
        r.add("cactus.events.relay.poll-interval", () -> "200ms");
        r.add("cactus.events.relay.base-backoff", () -> "500ms");
        r.add("cactus.events.relay.max-backoff", () -> "2s");
        r.add("cactus.events.relay.send-timeout", () -> "6s");
        r.add("cactus.events.consumer.group-id", () -> "it-" + UUID.randomUUID());
        r.add("cactus.events.consumer.max-retries", () -> "2");
        r.add("cactus.events.consumer.initial-backoff", () -> "100ms");
        r.add("cactus.events.consumer.max-backoff", () -> "300ms");
        r.add("spring.kafka.producer.properties.max.block.ms", () -> "2000");
        r.add("spring.kafka.producer.properties.request.timeout.ms", () -> "2000");
        r.add("spring.kafka.producer.properties.delivery.timeout.ms", () -> "5000");
        r.add("resilience4j.circuitbreaker.instances.kafka-outbox.wait-duration-in-open-state", () -> "2s");
    }

    /** Congela o broker (mantem portas): simula Kafka indisponivel sem mudar o endereco. */
    protected static void pauseBroker() {
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    protected static void resumeBroker() {
        KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }
}
