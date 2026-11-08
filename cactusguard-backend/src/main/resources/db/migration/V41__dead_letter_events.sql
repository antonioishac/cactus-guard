-- V41: registro das mensagens enviadas a DLQ (spec 047: nunca descartar silenciosamente).
-- Mensagem sem tenant valido e registrada no tenant 'platform' (00000000-0000-0000-0000-000000000002).
-- payload = envelope do evento (sem PII). Descarte exige justificativa e e auditado (audit_log).

CREATE TABLE dead_letter_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    event_id          UUID,
    source_topic      VARCHAR(200) NOT NULL,
    source_partition  INTEGER      NOT NULL,
    source_offset     BIGINT       NOT NULL,
    consumer_group    VARCHAR(100),
    record_key        VARCHAR(200),
    payload           TEXT,
    headers           JSONB,
    reason            VARCHAR(1000) NOT NULL,
    stack_trace       TEXT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'REPROCESSED', 'DISCARDED')),
    failed_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at       TIMESTAMP WITH TIME ZONE,
    resolved_by       VARCHAR(100),
    resolution_reason TEXT,
    CONSTRAINT fk_dead_letter_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    -- gravacao idempotente da mesma mensagem (a recuperacao pode ser repetida se a publicacao na DLQ falhar)
    CONSTRAINT uq_dead_letter_source UNIQUE (source_topic, source_partition, source_offset)
);

CREATE INDEX idx_dead_letter_tenant_status ON dead_letter_events (tenant_id, status, failed_at DESC);
CREATE INDEX idx_dead_letter_event_id ON dead_letter_events (event_id);

ALTER TABLE dead_letter_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE dead_letter_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON dead_letter_events
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));
