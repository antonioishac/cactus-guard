package br.com.cactus.guard.event;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.event.config.EventTopics;
import br.com.cactus.guard.event.consumer.NonRetryableEventException;
import br.com.cactus.guard.event.dto.DomainEventType;
import br.com.cactus.guard.event.dto.EventEnvelope;
import br.com.cactus.guard.event.service.EventPayloads;
import br.com.cactus.guard.event.service.PiiGuard;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import br.com.cactus.guard.transaction.entity.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Envelope CloudEvents-like, catalogo/roteamento e garantia de payload sem PII. */
class EventEnvelopeAndPiiTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void envelopeRoundTripKeepsAllFields() {
        UUID id = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        Instant at = Instant.parse("2026-11-08T12:00:00Z");
        EventEnvelope in = new EventEnvelope(id, "cactus.transaction.received", "cactusguard", tenant, "trace-12345678",
                at, 1, EventEnvelope.JSON, Map.of("transactionId", "abc", "amountBrl", "1234.50"));

        String json = mapper.writeValueAsString(in);
        EventEnvelope out = mapper.readValue(json, EventEnvelope.class);

        assertThat(out).isEqualTo(in);
        assertThat(json).contains("\"type\":\"cactus.transaction.received\"").contains("\"dataContentType\"")
                .contains("\"schemaVersion\":1");
    }

    @Test
    void everyEventTypeHasUniqueTypeAndKnownTopic() {
        Set<String> types = new java.util.HashSet<>();
        for (DomainEventType t : DomainEventType.values()) {
            assertThat(types.add(t.type())).as("tipo duplicado " + t).isTrue();
            assertThat(t.type()).startsWith("cactus.");
            assertThat(EventTopics.ALL).contains(t.topic());
            assertThat(DomainEventType.fromType(t.type())).contains(t);
        }
        assertThat(EventTopics.dlq(EventTopics.TRANSACTIONS)).isEqualTo("cactus.transactions.v1.dlq");
    }

    @Test
    void piiGuardRejectsPersonalFieldsRecursively() {
        for (String key : new String[]{"document", "partyDocument", "cpf", "cnpj", "email", "phone", "partyName",
                "counterpartyName", "name", "address", "birthDate", "counterparty_document"}) {
            assertThatThrownBy(() -> PiiGuard.assertNoPii(Map.of(key, "x"))).as(key)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> PiiGuard.assertNoPii(Map.of("nested", Map.of("e-mail", "x"))))
                .isInstanceOf(IllegalArgumentException.class);
        PiiGuard.assertNoPii(Map.of("transactionId", "1", "amountBrl", "10.00", "severity", "HIGH"));
    }

    @Test
    void builtPayloadsHaveNoPiiAndNoFreeText() {
        Party party = new Party();
        party.setId(UUID.randomUUID());
        party.setName("Fulano de Tal");
        party.setDocument("12345678901");

        Transaction t = Transaction.builder().id(UUID.randomUUID()).party(party).type("PIX").direction("IN")
                .amount(new BigDecimal("1500.10")).currency("BRL").amountBrl(new BigDecimal("1500.10"))
                .counterpartyName("Beltrano").counterpartyDocument("98765432100").channel("APP").status("COMPLETED")
                .transactionDate(Instant.now()).build();
        Alert a = Alert.builder().id(UUID.randomUUID()).party(party).type("TXN").severity("HIGH").ruleCode("X")
                .title("Parte Fulano de Tal").description("Fulano de Tal 12345678901")
                .totalAmount(new BigDecimal("10")).occurredAt(Instant.now()).build();
        Case c = new Case();
        c.setId(UUID.randomUUID());
        c.setParty(party);
        c.setStatus("PENDING_APPROVAL");
        c.setDecision("COMMUNICATE");
        c.setDecisionJustification("Fulano de Tal lavou dinheiro, CPF 12345678901");
        SiscoafCommunication comm = SiscoafCommunication.builder().id(UUID.randomUUID()).party(party).caseEntity(c)
                .type("COS").status("DRAFT").build();

        for (Map<String, Object> data : java.util.List.of(
                EventPayloads.transactionReceived(t), EventPayloads.alertCreated(a), EventPayloads.caseEvent(c),
                EventPayloads.communication(comm),
                EventPayloads.partyRiskChanged(party.getId(), "LOW", "HIGH", new BigDecimal("10"), new BigDecimal("80"), "T"),
                EventPayloads.assetFreezeActivated(party.getId(), "CSNU:1", UUID.randomUUID(), "CSNU"))) {
            PiiGuard.assertNoPii(data);
            String json = mapper.writeValueAsString(data);
            assertThat(json).doesNotContain("Fulano").doesNotContain("Beltrano").doesNotContain("12345678901")
                    .doesNotContain("98765432100").doesNotContain("lavou");
        }
        assertThat(EventPayloads.transactionReceived(t)).containsEntry("amountBrl", "1500.10");
    }

    @Test
    void errorHandlerClassifiesPayloadErrorsAsNotRetryable() {
        DefaultErrorHandler handler = new DefaultErrorHandler((r, e) -> { }, new ExponentialBackOffWithMaxRetries(2));
        handler.addNotRetryableExceptions(NonRetryableEventException.class);
        // classificacao: nao retentavel nao deve ser reclassificado como retentavel
        assertThat(handler.removeClassification(NonRetryableEventException.class)).isFalse();
    }
}
