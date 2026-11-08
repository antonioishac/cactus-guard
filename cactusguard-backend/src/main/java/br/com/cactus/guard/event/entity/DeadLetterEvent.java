package br.com.cactus.guard.event.entity;

import br.com.cactus.guard.tenant.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Mensagem que foi para a DLQ. Nunca e apagada: so muda de status (PENDING, REPROCESSED, DISCARDED). */
@Entity
@Table(name = "dead_letter_events")
@Getter
@Setter
@NoArgsConstructor
public class DeadLetterEvent extends TenantScoped {

    public static final String PENDING = "PENDING";
    public static final String REPROCESSED = "REPROCESSED";
    public static final String DISCARDED = "DISCARDED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "source_topic", nullable = false, length = 200, updatable = false)
    private String sourceTopic;

    @Column(name = "source_partition", nullable = false, updatable = false)
    private int sourcePartition;

    @Column(name = "source_offset", nullable = false, updatable = false)
    private long sourceOffset;

    @Column(name = "consumer_group", length = 100)
    private String consumerGroup;

    @Column(name = "record_key", length = 200)
    private String recordKey;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers")
    private Map<String, String> headers;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "status", nullable = false, length = 20)
    private String status = PENDING;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolution_reason", columnDefinition = "TEXT")
    private String resolutionReason;
}
