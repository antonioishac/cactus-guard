-- V40: idempotencia dos consumidores Kafka (spec 047). A linha e gravada na MESMA transacao do efeito do consumidor:
-- entrega duplicada (at-least-once) cai no ON CONFLICT e o efeito nao se repete.

CREATE TABLE processed_events (
    event_id     UUID         NOT NULL,
    consumer     VARCHAR(100) NOT NULL,
    tenant_id    UUID         NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer),
    CONSTRAINT fk_processed_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);
CREATE INDEX idx_processed_events_tenant ON processed_events (tenant_id);

ALTER TABLE processed_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE processed_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON processed_events
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));
