-- V39: eventos de dominio - outbox transacional (spec 047/048, Fase 1).
-- O servico de negocio grava o evento NA MESMA transacao do dado; o relay publica no Kafka depois (at-least-once).
-- Payload SEM PII: apenas ids e campos nao pessoais. Nao e dado regulatorio (esse fica nas tabelas de negocio e no
-- audit_log): linhas publicadas sao expurgadas apos N dias (cactus.events.cleanup.retention-days).
-- id = id do evento (chave de idempotencia dos consumidores). aggregate_id = chave de particao Kafka (ordem por agregado).

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    schema_version  INTEGER      NOT NULL DEFAULT 1,
    payload         JSONB        NOT NULL,
    headers         JSONB,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at    TIMESTAMP WITH TIME ZONE,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_error      VARCHAR(1000),
    CONSTRAINT fk_outbox_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

-- fila de publicacao (so pendentes) e verificacao de cabeca-de-fila por agregado
CREATE INDEX idx_outbox_pending ON outbox_events (tenant_id, occurred_at, id) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_pending_aggregate ON outbox_events (aggregate_id) WHERE published_at IS NULL;
-- expurgo de publicados
CREATE INDEX idx_outbox_published_at ON outbox_events (published_at) WHERE published_at IS NOT NULL;

ALTER TABLE outbox_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON outbox_events
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));
