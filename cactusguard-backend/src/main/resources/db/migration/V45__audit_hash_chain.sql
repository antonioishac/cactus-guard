-- V45: cadeia de integridade do audit_log (hash encadeado POR TENANT, calculada no banco) + ancoras.
--
-- hash = SHA-256( prev_hash || canonical(registro sem hash) ), onde canonical() e o jsonb do registro
-- (id, tenant, usuario, acao, entidade, old/new, ip, created_at UTC em microssegundos, chain_seq) em texto.
-- Cada tenant tem sua propria sequencia (chain_seq 1,2,3...). O calculo roda num trigger BEFORE INSERT que
-- serializa por tenant (SELECT ... FOR UPDATE em audit_chain_heads): inserts concorrentes NUNCA bifurcam a cadeia
-- (o segundo espera o commit do primeiro e le a cabeca ja atualizada). Custo: transacoes que auditam no mesmo tenant
-- se serializam ate o commit (documentado em specs/038 e 047).
--
-- HISTORICO ANTERIOR NAO E ENCADEADO: linhas pre-V45 ficam com chain_seq/hash/prev_hash NULL (reescreve-las exigiria
-- desligar o append-only da V22). A cadeia de cada tenant comeca num registro GENESIS (acao AUDIT_CHAIN_GENESIS,
-- chain_seq = 1, prev_hash = hash de genesis do tenant) criado por esta migration. Tenants criados depois nascem
-- com a cabeca de genesis no primeiro INSERT de auditoria (lazy, dentro do trigger).
--
-- Conformidade: Circular BCB 3.978/2020 art. 67 (guarda e integridade dos registros de analise).

ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS chain_seq BIGINT;

-- Um unico elo por posicao e tenant (defesa extra contra bifurcacao). Parcial: o legado nao encadeado fica de fora.
CREATE UNIQUE INDEX IF NOT EXISTS uq_audit_log_tenant_chain_seq
    ON audit_log (tenant_id, chain_seq) WHERE chain_seq IS NOT NULL;

-- Cabeca atual da cadeia de cada tenant.
CREATE TABLE audit_chain_heads (
    tenant_id  UUID PRIMARY KEY REFERENCES tenants(id),
    head_hash  VARCHAR(64) NOT NULL,
    head_seq   BIGINT      NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Ancoras: copia periodica e append-only da cabeca de cada tenant (tambem publicada como evento
-- cactus.audit.anchor no outbox), para prova externa de que o historico ate head_seq nao foi reescrito.
CREATE TABLE audit_chain_anchors (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    head_seq    BIGINT      NOT NULL,
    head_hash   VARCHAR(64) NOT NULL,
    anchored_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_audit_chain_anchor UNIQUE (tenant_id, head_seq)
);
CREATE INDEX idx_audit_chain_anchors_tenant_seq ON audit_chain_anchors (tenant_id, head_seq DESC);

-- Hash de genesis (deterministico por tenant).
CREATE OR REPLACE FUNCTION audit_genesis_hash(p_tenant uuid) RETURNS text
    LANGUAGE sql IMMUTABLE
AS $$ SELECT encode(sha256(convert_to('cactusguard:audit-chain:genesis:' || p_tenant::text, 'UTF8')), 'hex') $$;

-- Hash de uma linha: usado pelo trigger (NEW) e pela verificacao (linha lida) - uma unica definicao.
CREATE OR REPLACE FUNCTION audit_row_hash(p_prev text, r audit_log) RETURNS text
    LANGUAGE sql STABLE
AS $$
    SELECT encode(sha256(convert_to(
        coalesce(p_prev, '') || jsonb_build_object(
            'id', r.id,
            'tenantId', r.tenant_id,
            'userId', r.user_id,
            'action', r.action,
            'entityType', r.entity_type,
            'entityId', r.entity_id,
            'oldValue', r.old_value,
            'newValue', r.new_value,
            'ipAddress', r.ip_address,
            'createdAt', to_char(r.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
            'seq', r.chain_seq
        )::text, 'UTF8')), 'hex')
$$;

-- Trigger de encadeamento (BEFORE INSERT, por linha).
CREATE OR REPLACE FUNCTION audit_log_chain() RETURNS trigger AS $$
DECLARE
    v_prev text;
    v_seq  bigint;
BEGIN
    -- cabeca de genesis lazy (tenants novos); o conflito e a serializacao do primeiro insert concorrente
    INSERT INTO audit_chain_heads (tenant_id, head_hash, head_seq)
    VALUES (NEW.tenant_id, audit_genesis_hash(NEW.tenant_id), 0)
    ON CONFLICT (tenant_id) DO NOTHING;

    -- trava a cabeca do tenant ate o fim da transacao: inserts concorrentes do MESMO tenant esperam aqui
    SELECT head_hash, head_seq INTO v_prev, v_seq
      FROM audit_chain_heads WHERE tenant_id = NEW.tenant_id FOR UPDATE;

    NEW.id         := coalesce(NEW.id, gen_random_uuid());
    NEW.created_at := coalesce(NEW.created_at, now());
    NEW.prev_hash  := v_prev;
    NEW.chain_seq  := v_seq + 1;
    NEW.hash       := audit_row_hash(v_prev, NEW);

    UPDATE audit_chain_heads
       SET head_hash = NEW.hash, head_seq = NEW.chain_seq, updated_at = now()
     WHERE tenant_id = NEW.tenant_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Cabeca de genesis dos tenants existentes (antes dos triggers de protecao abaixo).
INSERT INTO audit_chain_heads (tenant_id, head_hash, head_seq)
SELECT id, audit_genesis_hash(id), 0 FROM tenants;

DROP TRIGGER IF EXISTS audit_log_chain_hash ON audit_log;
CREATE TRIGGER audit_log_chain_hash
    BEFORE INSERT ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_chain();

-- Registro GENESIS visivel na trilha (marca o inicio do encadeamento de cada tenant).
-- Requer papel de migration com BYPASSRLS/superusuario (mesma premissa da V34/V38).
INSERT INTO audit_log (tenant_id, action, entity_type, new_value)
SELECT id, 'AUDIT_CHAIN_GENESIS', 'AUDIT_CHAIN',
       jsonb_build_object('note', 'inicio do encadeamento; registros anteriores a V45 nao sao encadeados')
  FROM tenants;

-- Protecao das tabelas de cadeia. A cabeca so muda DENTRO do trigger de auditoria (pg_trigger_depth() >= 2):
-- nenhum INSERT/UPDATE direto, nunca DELETE/TRUNCATE. Ancoras: append-only.
CREATE OR REPLACE FUNCTION audit_chain_heads_protect() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'TRUNCATE' OR TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'audit_chain_heads: % nao permitido', TG_OP USING ERRCODE = 'insufficient_privilege';
    END IF;
    IF pg_trigger_depth() < 2 THEN
        RAISE EXCEPTION 'audit_chain_heads so pode ser alterada pelo trigger de auditoria' USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_chain_heads_guard
    BEFORE INSERT OR UPDATE OR DELETE ON audit_chain_heads
    FOR EACH ROW EXECUTE FUNCTION audit_chain_heads_protect();
CREATE TRIGGER audit_chain_heads_no_truncate
    BEFORE TRUNCATE ON audit_chain_heads
    FOR EACH STATEMENT EXECUTE FUNCTION audit_chain_heads_protect();

CREATE OR REPLACE FUNCTION audit_chain_anchors_protect() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_chain_anchors e append-only: % nao permitido', TG_OP USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_chain_anchors_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_chain_anchors
    FOR EACH ROW EXECUTE FUNCTION audit_chain_anchors_protect();
CREATE TRIGGER audit_chain_anchors_no_truncate
    BEFORE TRUNCATE ON audit_chain_anchors
    FOR EACH STATEMENT EXECUTE FUNCTION audit_chain_anchors_protect();

-- RLS (mesmo padrao das demais tabelas por tenant)
ALTER TABLE audit_chain_heads ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_chain_heads FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_chain_heads
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE audit_chain_anchors ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_chain_anchors FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_chain_anchors
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));
