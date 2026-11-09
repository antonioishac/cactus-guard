package br.com.cactus.guard.event.dto;

import br.com.cactus.guard.event.config.EventTopics;

import java.util.Arrays;
import java.util.Optional;

/** Catalogo de eventos de dominio: tipo (CloudEvents), tipo do agregado e topico de destino. */
public enum DomainEventType {

    TRANSACTION_RECEIVED("cactus.transaction.received", "TRANSACTION", EventTopics.TRANSACTIONS),
    ALERT_CREATED("cactus.alert.created", "ALERT", EventTopics.ALERTS),
    CASE_DECIDED("cactus.case.decided", "CASE", EventTopics.CASES),
    CASE_APPROVED("cactus.case.approved", "CASE", EventTopics.CASES),
    CASE_APPROVAL_REJECTED("cactus.case.approval-rejected", "CASE", EventTopics.CASES),
    COMMUNICATION_CREATED("cactus.communication.created", "COMMUNICATION", EventTopics.COMMUNICATIONS),
    COMMUNICATION_SUBMITTED("cactus.communication.submitted", "COMMUNICATION", EventTopics.COMMUNICATIONS),
    PARTY_RISK_CHANGED("cactus.party.risk-changed", "PARTY", EventTopics.PARTIES),
    ASSET_FREEZE_ACTIVATED("cactus.party.asset-freeze-activated", "PARTY", EventTopics.PARTIES),
    /** Ancora periodica da cabeca da cadeia de hash do audit_log do tenant (V45); so hash e sequencia, sem PII. */
    AUDIT_ANCHOR("cactus.audit.anchor", "AUDIT_ANCHOR", EventTopics.AUDIT);

    private final String type;
    private final String aggregateType;
    private final String topic;

    DomainEventType(String type, String aggregateType, String topic) {
        this.type = type;
        this.aggregateType = aggregateType;
        this.topic = topic;
    }

    public String type() {
        return type;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String topic() {
        return topic;
    }

    public int schemaVersion() {
        return 1;
    }

    public static Optional<DomainEventType> fromType(String type) {
        return Arrays.stream(values()).filter(t -> t.type.equals(type)).findFirst();
    }
}
