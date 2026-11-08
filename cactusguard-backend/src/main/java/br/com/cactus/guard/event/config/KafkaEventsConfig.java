package br.com.cactus.guard.event.config;

import br.com.cactus.guard.event.consumer.NonRetryableEventException;
import br.com.cactus.guard.event.service.DeadLetterService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.ArrayList;
import java.util.List;

/**
 * Infra Kafka dos eventos de dominio (somente com {@code cactus.events.enabled=true}):
 * <ul>
 *   <li>Topicos declarados pela aplicacao (auto-criacao esta desligada no cluster): principal + {@code .dlq}.</li>
 *   <li>Tratamento de erro dos consumidores: retentativas bloqueantes com backoff exponencial (preserva a ordem
 *       por agregado) e, esgotadas, registro em dead_letter_events + publicacao em {@code <topico>.dlq}. Erros nao
 *       recuperaveis (payload invalido, tenant invalido, agregado inexistente) vao direto a DLQ.
 *       A mensagem so tem o offset confirmado depois de registrada e publicada na DLQ: nunca e descartada.</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "cactus.events", name = "enabled", havingValue = "true")
public class KafkaEventsConfig {

    @Bean
    @ConditionalOnProperty(prefix = "cactus.events.topics", name = "create", havingValue = "true", matchIfMissing = true)
    public KafkaAdmin.NewTopics cactusEventTopics(EventProperties props) {
        EventProperties.Topics t = props.getTopics();
        List<NewTopic> topics = new ArrayList<>();
        for (String name : EventTopics.ALL) {
            topics.add(TopicBuilder.name(name).partitions(t.getPartitions()).replicas(t.getReplicationFactor())
                    .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(t.getRetention().toMillis())).build());
            topics.add(TopicBuilder.name(EventTopics.dlq(name)).partitions(t.getPartitions())
                    .replicas(t.getReplicationFactor())
                    .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(t.getDlqRetention().toMillis())).build());
        }
        return new KafkaAdmin.NewTopics(topics.toArray(new NewTopic[0]));
    }

    /** 1) registra em dead_letter_events; 2) publica em {@code <topico>.dlq}. Qualquer falha reentrega a mensagem. */
    @Bean
    public ConsumerRecordRecoverer cactusDeadLetterRecoverer(KafkaTemplate<String, String> template,
                                                             DeadLetterService deadLetters, EventProperties props) {
        DeadLetterPublishingRecoverer publisher = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(EventTopics.dlq(record.topic()), -1));
        return (record, ex) -> {
            deadLetters.record(record, ex, props.getConsumer().getGroupId());
            publisher.accept(record, ex);
        };
    }

    /** Boot aplica este bean ao container factory padrao dos {@code @KafkaListener}. */
    @Bean
    public CommonErrorHandler cactusKafkaErrorHandler(ConsumerRecordRecoverer cactusDeadLetterRecoverer,
                                                      EventProperties props) {
        EventProperties.Consumer c = props.getConsumer();
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(c.getMaxRetries());
        backOff.setInitialInterval(c.getInitialBackoff().toMillis());
        backOff.setMultiplier(c.getBackoffMultiplier());
        backOff.setMaxInterval(c.getMaxBackoff().toMillis());
        DefaultErrorHandler handler = new DefaultErrorHandler(cactusDeadLetterRecoverer, backOff);
        handler.addNotRetryableExceptions(NonRetryableEventException.class);
        handler.setCommitRecovered(true);
        return handler;
    }
}
