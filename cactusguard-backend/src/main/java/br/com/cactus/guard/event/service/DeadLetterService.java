package br.com.cactus.guard.event.service;

import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.common.audit.AuditPayload;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.exception.NotFoundException;
import br.com.cactus.guard.common.security.CurrentUserService;
import br.com.cactus.guard.common.validation.Justifications;
import br.com.cactus.guard.event.dto.DeadLetterResponse;
import br.com.cactus.guard.event.entity.DeadLetterEvent;
import br.com.cactus.guard.event.repository.DeadLetterEventRepository;
import br.com.cactus.guard.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Registro e tratamento das mensagens em DLQ (spec 047: nunca descartar silenciosamente).
 * <ul>
 *   <li>{@link #record}: grava a falha em dead_letter_events (idempotente por topico/particao/offset).</li>
 *   <li>{@link #reprocess}: republica a mensagem no topico de origem (o consumidor deduplica por id do evento).</li>
 *   <li>{@link #discard}: exige justificativa; auditado. A linha NUNCA e apagada.</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "cactus.events", name = "enabled", havingValue = "true")
public class DeadLetterService {

    private static final int MAX_REASON = 1000;
    private static final int MAX_STACK = 4000;
    private static final int STACK_FRAMES = 12;

    private final DeadLetterEventRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final AuditService auditService;
    private final CurrentUserService currentUser;
    private final TransactionTemplate tx;

    public DeadLetterService(DeadLetterEventRepository repository, KafkaTemplate<String, String> kafka,
                             AuditService auditService, CurrentUserService currentUser,
                             PlatformTransactionManager txManager) {
        this.repository = repository;
        this.kafka = kafka;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ registro (thread do consumidor)

    /** Grava a falha. Sem tenant valido nos cabecalhos (ou tenant desconhecido), registra no tenant 'platform'. */
    public void record(ConsumerRecord<?, ?> rec, Exception failure, String fallbackGroup) {
        UUID tenant = parseTenant(headerValue(rec, DomainEventPublisher.HEADER_TENANT));
        String group = failure instanceof ListenerExecutionFailedException l && l.getGroupId() != null
                ? l.getGroupId() : fallbackGroup;
        try {
            saveUnder(tenant != null ? tenant : TenantContext.PLATFORM_TENANT_ID, rec, failure, group, tenant == null);
        } catch (DataIntegrityViolationException e) {
            if (tenant == null || TenantContext.PLATFORM_TENANT_ID.equals(tenant)) {
                throw e;
            }
            // tenant do cabecalho nao existe: nao perde o registro
            saveUnder(TenantContext.PLATFORM_TENANT_ID, rec, failure, group, true);
        }
    }

    private void saveUnder(UUID tenant, ConsumerRecord<?, ?> rec, Exception failure, String group, boolean unknownTenant) {
        TenantContext.runAs(tenant, () -> tx.executeWithoutResult(status -> {
            if (repository.existsBySourceTopicAndSourcePartitionAndSourceOffset(rec.topic(), rec.partition(), rec.offset())) {
                return; // recuperacao repetida (a publicacao na DLQ falhou antes): ja registrado
            }
            Throwable root = rootCause(failure);
            DeadLetterEvent d = new DeadLetterEvent();
            d.setEventId(parseUuid(headerValue(rec, "eventId")));
            d.setSourceTopic(rec.topic());
            d.setSourcePartition(rec.partition());
            d.setSourceOffset(rec.offset());
            d.setConsumerGroup(group);
            d.setRecordKey(rec.key() == null ? null : truncate(String.valueOf(rec.key()), 200));
            d.setPayload(rec.value() == null ? null : String.valueOf(rec.value()));
            d.setHeaders(copyHeaders(rec));
            String reason = root.getClass().getSimpleName() + ": " + root.getMessage();
            d.setReason(truncate((unknownTenant ? "[tenant ausente/desconhecido] " : "") + reason, MAX_REASON));
            d.setStackTrace(stackSummary(root));
            repository.save(d);
            log.warn("Mensagem {}-{}@{} enviada a DLQ e registrada: {}", rec.topic(), rec.partition(), rec.offset(),
                    root.getClass().getSimpleName());
        }));
    }

    // ------------------------------------------------------------------ operacao (ADMIN/SUPERVISOR)

    @Transactional(readOnly = true)
    public Page<DeadLetterResponse> list(String status, Pageable pageable) {
        Page<DeadLetterEvent> page = status == null || status.isBlank()
                ? repository.findAll(pageable) : repository.findByStatus(status.trim().toUpperCase(), pageable);
        return page.map(DeadLetterResponse::from);
    }

    @Transactional
    public DeadLetterResponse reprocess(UUID id, String note) {
        DeadLetterEvent d = pending(id);
        ProducerRecord<String, String> out = new ProducerRecord<>(d.getSourceTopic(), d.getRecordKey(), d.getPayload());
        if (d.getHeaders() != null) {
            d.getHeaders().forEach((k, v) -> out.headers().add(k, v.getBytes(StandardCharsets.UTF_8)));
        }
        out.headers().add("x-reprocessed-from", id.toString().getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(out).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Reprocessamento interrompido; a mensagem continua pendente");
        } catch (Exception e) {
            log.error("Falha ao republicar a mensagem da DLQ {}", id, e);
            throw new BusinessException("Nao foi possivel republicar a mensagem agora; ela continua pendente");
        }
        resolve(d, DeadLetterEvent.REPROCESSED, note == null || note.isBlank() ? null : note.trim());
        audit("DLQ_REPROCESSED", d, note);
        return DeadLetterResponse.from(d);
    }

    @Transactional
    public DeadLetterResponse discard(UUID id, String justification) {
        String just = Justifications.require(justification, "descartar mensagem da DLQ");
        DeadLetterEvent d = pending(id);
        resolve(d, DeadLetterEvent.DISCARDED, just);
        audit("DLQ_DISCARDED", d, just);
        return DeadLetterResponse.from(d);
    }

    private DeadLetterEvent pending(UUID id) {
        DeadLetterEvent d = repository.findById(id).orElseThrow(() -> new NotFoundException("Mensagem da DLQ nao encontrada"));
        if (!DeadLetterEvent.PENDING.equals(d.getStatus())) {
            throw new BusinessException("Mensagem ja tratada (status " + d.getStatus() + ")");
        }
        return d;
    }

    private void resolve(DeadLetterEvent d, String status, String reason) {
        d.setStatus(status);
        d.setResolvedAt(Instant.now());
        d.setResolvedBy(currentUser.user().map(u -> u.getUsername()).orElse("system"));
        d.setResolutionReason(reason);
        repository.save(d);
    }

    private void audit(String action, DeadLetterEvent d, String reason) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", d.getStatus());
        after.put("sourceTopic", d.getSourceTopic());
        after.put("sourcePartition", d.getSourcePartition());
        after.put("sourceOffset", d.getSourceOffset());
        after.put("eventId", d.getEventId() == null ? null : d.getEventId().toString());
        if (reason != null && !reason.isBlank()) {
            after.put("justification", reason.trim());
        }
        AuditPayload.log(auditService, action, "DEAD_LETTER_EVENT", d.getId(), Map.of("status", DeadLetterEvent.PENDING), after);
    }

    // ------------------------------------------------------------------ utilitarios

    private static Map<String, String> copyHeaders(ConsumerRecord<?, ?> rec) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Header h : rec.headers()) {
            if (h.key().startsWith("kafka_") || h.value() == null || h.value().length > 500) {
                continue;
            }
            m.put(h.key(), new String(h.value(), StandardCharsets.UTF_8));
        }
        return m;
    }

    private static String headerValue(ConsumerRecord<?, ?> rec, String name) {
        Header h = rec.headers().lastHeader(name);
        return h == null || h.value() == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static UUID parseTenant(String v) {
        UUID t = parseUuid(v);
        return t == null || TenantContext.NO_TENANT.equals(t) ? null : t;
    }

    private static UUID parseUuid(String v) {
        try {
            return v == null ? null : UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    static String stackSummary(Throwable t) {
        StringBuilder sb = new StringBuilder(t.getClass().getName());
        StackTraceElement[] frames = t.getStackTrace();
        for (int i = 0; i < Math.min(frames.length, STACK_FRAMES); i++) {
            sb.append("\n  at ").append(frames[i]);
        }
        return truncate(sb.toString(), MAX_STACK);
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
