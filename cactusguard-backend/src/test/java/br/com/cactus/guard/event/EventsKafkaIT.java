package br.com.cactus.guard.event;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.util.DocumentHash;
import br.com.cactus.guard.event.config.EventTopics;
import br.com.cactus.guard.event.dto.DeadLetterResponse;
import br.com.cactus.guard.event.dto.EventEnvelope;
import br.com.cactus.guard.event.service.DeadLetterService;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.support.AbstractKafkaPostgresIT;
import br.com.cactus.guard.tenant.TenantContext;
import br.com.cactus.guard.tenant.entity.Tenant;
import br.com.cactus.guard.tenant.repository.TenantRepository;
import br.com.cactus.guard.transaction.dto.TransactionRequest;
import br.com.cactus.guard.transaction.entity.Transaction;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import br.com.cactus.guard.transaction.service.TransactionService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Criterio de saida da Fase 1 (spec 048): nenhum evento e perdido mesmo com o Kafka fora do ar e os consumidores sao
 * idempotentes. PostgreSQL + Kafka reais (Testcontainers); a aplicacao roda sem bypass de RLS.
 */
class EventsKafkaIT extends AbstractKafkaPostgresIT {

    @Autowired private TransactionService transactionService;
    @Autowired private PartyRepository partyRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private MonitoringRuleRepository ruleRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private DeadLetterService deadLetterService;
    @Autowired private KafkaTemplate<String, String> kafka;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    // ------------------------------------------------------------------ helpers

    private UUID newParty() {
        String doc = String.format("%011d", ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L));
        return partyRepository.save(Party.builder().type("PF").document(doc).documentHash(DocumentHash.of(doc))
                .name("Parte Evento " + doc).status("ACTIVE").riskLevel("LOW").build()).getId();
    }

    private UUID bigTransaction(UUID partyId) {
        TransactionRequest r = new TransactionRequest();
        r.setPartyId(partyId);
        r.setType("PIX");
        r.setAmount(new BigDecimal("80000.00"));
        r.setDirection("IN");
        r.setChannel("APP");
        r.setTransactionDate(Instant.now());
        return transactionService.create(r).getId();
    }

    private List<Alert> alertsOf(UUID partyId) {
        return alertRepository.findByPartyId(partyId, PageRequest.of(0, 50)).getContent();
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private List<ConsumerRecord<String, String>> readTopic(String topic, Duration window) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "it-reader-" + UUID.randomUUID());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        try (KafkaConsumer<String, String> c = new KafkaConsumer<>(p)) {
            c.subscribe(List.of(topic));
            long end = System.currentTimeMillis() + window.toMillis();
            while (System.currentTimeMillis() < end) {
                c.poll(Duration.ofMillis(300)).forEach(out::add);
            }
        }
        return out;
    }

    private String envelopeJson(UUID eventId, UUID tenantId, UUID transactionId) {
        return mapper.writeValueAsString(new EventEnvelope(eventId, "cactus.transaction.received", "it", tenantId,
                "trace-manual-1234", Instant.now(), 1, EventEnvelope.JSON, Map.of("transactionId", transactionId.toString())));
    }

    private void sendRaw(String key, String value, UUID eventId, UUID headerTenant) throws Exception {
        ProducerRecord<String, String> rec = new ProducerRecord<>(EventTopics.TRANSACTIONS, key, value);
        rec.headers().add("tenantId", headerTenant.toString().getBytes(StandardCharsets.UTF_8));
        rec.headers().add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
        rec.headers().add("traceId", "trace-manual-1234".getBytes(StandardCharsets.UTF_8));
        kafka.send(rec).get();
    }

    private List<Map<String, Object>> deadLetters(UUID eventId) {
        return jdbc.queryForList("select id, status, reason, resolved_by from dead_letter_events where event_id = ? order by failed_at", eventId);
    }

    // ------------------------------------------------------------------ (a) transacao -> alerta em segundos

    @Test
    void transactionBecomesAlertWithinSecondsAndEventHasNoPii() {
        UUID partyId = newParty();
        MDC.put("traceId", "trace-it-a-12345678");
        UUID txnId;
        try {
            txnId = bigTransaction(partyId);
        } finally {
            MDC.remove("traceId");
        }
        long start = System.currentTimeMillis();

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).pollInSameThread()
                .untilAsserted(() -> assertThat(alertsOf(partyId)).extracting(Alert::getRuleCode).contains("TXN_HIGH_VALUE"));
        assertThat(System.currentTimeMillis() - start).as("alerta gerado pelo consumidor, nao pelo batch de 6h")
                .isLessThan(30_000);

        // exatamente um efeito por evento
        assertThat(alertsOf(partyId).stream().filter(a -> "TXN_HIGH_VALUE".equals(a.getRuleCode())).count()).isEqualTo(1);
        assertThat(count("select count(*) from processed_events where consumer = 'monitoring-transactions' "
                + "and event_id in (select id from outbox_events where aggregate_id = ?)", txnId)).isEqualTo(1);

        // o alerta tambem gerou AlertCreated no outbox e foi publicado
        await().atMost(Duration.ofSeconds(20)).pollInSameThread().untilAsserted(() ->
                assertThat(count("select count(*) from outbox_events where event_type = 'cactus.alert.created' "
                        + "and published_at is not null and payload->>'partyId' = ?", partyId.toString())).isEqualTo(1));

        // o evento no topico: chave = agregado, cabecalhos de tenant/trace, sem PII
        List<ConsumerRecord<String, String>> onTopic = readTopic(EventTopics.TRANSACTIONS, Duration.ofSeconds(5)).stream()
                .filter(r -> txnId.toString().equals(r.key())).toList();
        assertThat(onTopic).isNotEmpty();
        ConsumerRecord<String, String> rec = onTopic.get(0);
        assertThat(new String(rec.headers().lastHeader("traceId").value(), StandardCharsets.UTF_8)).isEqualTo("trace-it-a-12345678");
        assertThat(new String(rec.headers().lastHeader("tenantId").value(), StandardCharsets.UTF_8))
                .isEqualTo(TenantContext.DEFAULT_TENANT_ID.toString());
        assertThat(new String(rec.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                .isEqualTo("cactus.transaction.received");
        assertThat(rec.value()).doesNotContain("Parte Evento").contains("\"amountBrl\"").doesNotContain("document");
    }

    // ------------------------------------------------------------------ (b) broker fora do ar

    @Test
    void brokerDownKeepsEventsInOutboxAndEverythingArrivesExactlyOnceEffectAfterRecovery() {
        int n = 5;
        List<UUID> parties = new ArrayList<>();
        List<UUID> txns = new ArrayList<>();
        pauseBroker();
        try {
            for (int i = 0; i < n; i++) {
                UUID p = newParty();
                parties.add(p);
                txns.add(bigTransaction(p)); // o negocio segue funcionando com o Kafka indisponivel
            }
            // dado de negocio e eventos gravados juntos...
            assertThat(count("select count(*) from outbox_events where aggregate_id = any(?::uuid[])",
                    (Object) txns.stream().map(UUID::toString).toArray(String[]::new))).isEqualTo(n);
            // ...e, com o broker parado, nada e publicado (as tentativas ficam registradas)
            await().atMost(Duration.ofSeconds(40)).pollInSameThread().untilAsserted(() ->
                    assertThat(count("select coalesce(sum(attempts),0) from outbox_events where published_at is null "
                            + "and aggregate_id = any(?::uuid[])",
                            (Object) txns.stream().map(UUID::toString).toArray(String[]::new))).isGreaterThan(0));
            assertThat(count("select count(*) from outbox_events where published_at is not null "
                    + "and aggregate_id = any(?::uuid[])",
                    (Object) txns.stream().map(UUID::toString).toArray(String[]::new))).isZero();
            parties.forEach(p -> assertThat(alertsOf(p)).isEmpty());
        } finally {
            resumeBroker();
        }

        String[] txnIds = txns.stream().map(UUID::toString).toArray(String[]::new);
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(500)).pollInSameThread().untilAsserted(() -> {
            assertThat(count("select count(*) from outbox_events where published_at is not null "
                    + "and aggregate_id = any(?::uuid[]) and event_type = 'cactus.transaction.received'",
                    (Object) txnIds)).isEqualTo(n);
            for (UUID p : parties) {
                assertThat(alertsOf(p).stream().filter(a -> "TXN_HIGH_VALUE".equals(a.getRuleCode())).count())
                        .as("alerta da parte " + p).isEqualTo(1);
            }
        });
        // exatamente uma marca de processamento por evento (duplicatas de entrega, se houve, foram absorvidas)
        assertThat(count("select count(*) from processed_events where consumer = 'monitoring-transactions' "
                + "and event_id in (select id from outbox_events where aggregate_id = any(?::uuid[]))",
                (Object) txnIds)).isEqualTo(n);
    }

    // ------------------------------------------------------------------ (c) mensagem envenenada -> DLQ

    @Test
    void poisonMessageGoesToDlqIsRecordedAndCanBeReprocessedOrDiscardedWithJustification() throws Exception {
        UUID poisonId = UUID.randomUUID();
        sendRaw("poison-key", "{isto nao e json", poisonId, TenantContext.DEFAULT_TENANT_ID);

        await().atMost(Duration.ofSeconds(30)).pollInSameThread()
                .untilAsserted(() -> assertThat(deadLetters(poisonId)).hasSize(1));
        Map<String, Object> row = deadLetters(poisonId).get(0);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(String.valueOf(row.get("reason"))).contains("NonRetryableEventException").doesNotContain("isto nao e json");
        assertThat(readTopic(EventTopics.dlq(EventTopics.TRANSACTIONS), Duration.ofSeconds(5)))
                .anyMatch(r -> "poison-key".equals(r.key()));

        // reprocessar: republica no topico de origem; como o payload segue invalido volta a DLQ (novo registro)
        UUID id = (UUID) row.get("id");
        DeadLetterResponse reprocessed = deadLetterService.reprocess(id, "reprocesso de teste");
        assertThat(reprocessed.status()).isEqualTo("REPROCESSED");
        await().atMost(Duration.ofSeconds(30)).pollInSameThread()
                .untilAsserted(() -> assertThat(deadLetters(poisonId)).hasSize(2));
        assertThat(deadLetters(poisonId).get(1).get("status")).isEqualTo("PENDING");
        assertThatThrownBy(() -> deadLetterService.reprocess(id, null)).isInstanceOf(BusinessException.class);

        // descartar exige justificativa e e auditado; a linha nunca some
        UUID second = (UUID) deadLetters(poisonId).get(1).get("id");
        assertThatThrownBy(() -> deadLetterService.discard(second, "curto")).isInstanceOf(BusinessException.class);
        assertThat(deadLetterService.discard(second, "Mensagem de teste com payload invalido, sem efeito de negocio").status())
                .isEqualTo("DISCARDED");
        assertThat(deadLetters(poisonId)).hasSize(2);
        assertThat(count("select count(*) from audit_log where action in ('DLQ_DISCARDED','DLQ_REPROCESSED') and entity_id in (?, ?)",
                id, second)).isEqualTo(2);
    }

    @Test
    void messageThatFailedBecauseTheAggregateWasMissingIsProcessedAfterReprocessing() throws Exception {
        UUID partyId = newParty();
        UUID futureTxn = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        sendRaw(futureTxn.toString(), envelopeJson(eventId, TenantContext.DEFAULT_TENANT_ID, futureTxn), eventId,
                TenantContext.DEFAULT_TENANT_ID);
        await().atMost(Duration.ofSeconds(30)).pollInSameThread()
                .untilAsserted(() -> assertThat(deadLetters(eventId)).hasSize(1));
        UUID dlId = (UUID) deadLetters(eventId).get(0).get("id");
        assertThat(alertsOf(partyId)).isEmpty();

        // a "causa" e corrigida (a transacao passa a existir) e a mensagem e reprocessada
        jdbc.update("insert into transactions (id, tenant_id, party_id, type, amount, currency, status, transaction_date, direction, channel) "
                        + "values (?,?,?,?,?,?,?,?,?,?)", futureTxn, TenantContext.DEFAULT_TENANT_ID, partyId, "PIX",
                new BigDecimal("90000.00"), "BRL", "COMPLETED", Timestamp.from(Instant.now()), "IN", "APP");
        deadLetterService.reprocess(dlId, "transacao criada apos o evento");

        await().atMost(Duration.ofSeconds(30)).pollInSameThread()
                .untilAsserted(() -> assertThat(alertsOf(partyId)).extracting(Alert::getRuleCode).contains("TXN_HIGH_VALUE"));
        assertThat(deadLetters(eventId)).hasSize(1);
        assertThat(deadLetters(eventId).get(0).get("status")).isEqualTo("REPROCESSED");
    }

    // ------------------------------------------------------------------ (d) isolamento de tenant no consumo

    @Test
    void eventOfTenantBNeverRunsRulesInTenantA() throws Exception {
        UUID tenantB = UUID.randomUUID();
        tenantRepository.save(Tenant.builder().id(tenantB).code("it-ev-" + tenantB.toString().substring(0, 8))
                .name("IT eventos B").regulatorySegment("BANK").build());

        UUID partyB = TenantContext.callAs(tenantB, () -> {
            ruleRepository.save(MonitoringRule.builder().code("TXN_HIGH_VALUE").name("Alto valor").category("TRANSACTIONAL")
                    .severity("HIGH").parameters(Map.of("threshold_brl", 50000, "window_days", 30)).build());
            return newParty();
        });
        UUID txnB = TenantContext.callAs(tenantB, () -> transactionRepository.save(Transaction.builder()
                .party(partyRepository.getReferenceById(partyB)).type("PIX").amount(new BigDecimal("95000.00"))
                .currency("BRL").direction("IN").channel("APP").transactionDate(Instant.now()).build()).getId());

        long alertsA = alertRepository.count();

        // 1) evento legitimo do B: roda as regras de B (gera alerta em B), jamais em A
        UUID okEvent = UUID.randomUUID();
        sendRaw(txnB.toString(), envelopeJson(okEvent, tenantB, txnB), okEvent, tenantB);
        await().atMost(Duration.ofSeconds(30)).pollInSameThread().untilAsserted(() ->
                assertThat(TenantContext.callAs(tenantB, () -> alertsOf(partyB))).isNotEmpty());
        assertThat(alertsOf(partyB)).isEmpty();
        assertThat(alertRepository.count()).isEqualTo(alertsA);

        // 2) forjado: cabecalho/corpo do A apontando para a transacao do B -> invisivel no A -> DLQ, sem efeito
        UUID forged = UUID.randomUUID();
        sendRaw(txnB.toString(), envelopeJson(forged, TenantContext.DEFAULT_TENANT_ID, txnB), forged,
                TenantContext.DEFAULT_TENANT_ID);
        await().atMost(Duration.ofSeconds(30)).pollInSameThread().untilAsserted(() -> assertThat(deadLetters(forged)).hasSize(1));
        assertThat(alertRepository.count()).isEqualTo(alertsA);

        // 3) corpo de um tenant e cabecalho de outro -> rejeitado (DLQ no tenant do cabecalho)
        UUID mismatch = UUID.randomUUID();
        sendRaw(txnB.toString(), envelopeJson(mismatch, TenantContext.DEFAULT_TENANT_ID, txnB), mismatch, tenantB);
        await().atMost(Duration.ofSeconds(30)).pollInSameThread().untilAsserted(() ->
                assertThat(TenantContext.callAs(tenantB, () -> deadLetters(mismatch))).hasSize(1));
        assertThat(deadLetters(mismatch)).isEmpty();
        assertThat(alertRepository.count()).isEqualTo(alertsA);
    }
}
