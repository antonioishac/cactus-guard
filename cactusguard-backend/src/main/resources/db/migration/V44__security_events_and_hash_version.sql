-- V44: endurecimento - (a) security_events (tentativas de login) e (b) versao do pepper de hash por tenant.

-- (a) security_events: evento de PLATAFORMA (login e anterior a existir tenant no contexto e usernames inexistentes
-- nao pertencem a nenhum tenant). Por isso NAO tem RLS nem tenant_id obrigatorio: user_tenant_id e so informativo
-- (preenchido quando o usuario existe), sem FK. Acesso apenas pelo backend; leitura exposta so a PLATFORM_ADMIN
-- (GET /api/v1/platform/security-events). NUNCA guarda a senha tentada; o username fica mascarado (username_masked) e
-- o contador de bloqueio usa username_hash (HMAC-SHA256 com segredo da aplicacao, nao reversivel sem o pepper).
-- Append-only: UPDATE proibido; DELETE so de linhas com mais de 2 anos (retencao de seguranca).
CREATE TABLE security_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(40)  NOT NULL,   -- LOGIN_FAILURE | LOGIN_SUCCESS | LOGIN_BLOCKED | LOGIN_USER_LOCKED | LOGIN_IP_LOCKED | LOGIN_DENIED_TENANT_SUSPENDED
    result          VARCHAR(20)  NOT NULL,   -- SUCCESS | FAILURE | BLOCKED | DENIED
    username_masked VARCHAR(100),
    username_hash   VARCHAR(64),
    user_tenant_id  UUID,                    -- informativo (nao e chave de isolamento; por isso nao se chama tenant_id)
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(200),
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_security_events_user_time ON security_events(username_hash, occurred_at DESC);
CREATE INDEX idx_security_events_ip_time   ON security_events(ip_address, occurred_at DESC);
CREATE INDEX idx_security_events_time      ON security_events(occurred_at DESC);
CREATE INDEX idx_security_events_type_time ON security_events(event_type, occurred_at DESC);

CREATE OR REPLACE FUNCTION security_events_protect() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'security_events e append-only: UPDATE nao permitido (id=%)', OLD.id
            USING ERRCODE = 'insufficient_privilege';
    ELSIF TG_OP = 'DELETE' THEN
        IF OLD.occurred_at < now() - interval '2 years' THEN
            RETURN OLD;
        END IF;
        RAISE EXCEPTION 'security_events e append-only: DELETE permitido apenas para registros com mais de 2 anos (id=%)', OLD.id
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER security_events_no_update_delete
    BEFORE UPDATE OR DELETE ON security_events
    FOR EACH ROW EXECUTE FUNCTION security_events_protect();

-- (b) hash_version: 1 = document_hash com pepper global (legado); 2 = pepper por tenant (HKDF a partir de
-- DOCUMENT_HASH_KEY + tenantId). Tenants existentes ficam em 1 e a aplicacao os migra uma unica vez na subida
-- (DocumentHashMigrationRunner); tenants criados dali em diante ja nascem em 2.
ALTER TABLE tenants ADD COLUMN hash_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE tenants ALTER COLUMN hash_version SET DEFAULT 2;
