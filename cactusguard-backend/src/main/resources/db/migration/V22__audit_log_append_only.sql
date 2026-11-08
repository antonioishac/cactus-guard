-- V22: audit_log append-only + colunas para cadeia de integridade
--
-- Circular BCB 3.978/2020 exige a guarda de registros e da documentacao de analise por
-- no minimo 10 anos (art. 67). O log de auditoria e prova da trilha de decisoes, logo:
--   * UPDATE e sempre proibido;
--   * DELETE so e aceito para linhas com mais de 10 anos (job de retencao);
--   * TRUNCATE e proibido.
-- Obs.: o DataRetentionJob atual usa corte de 5 anos; enquanto nao for ajustado para
-- 10 anos, a exclusao de linhas entre 5 e 10 anos sera rejeitada por este trigger.

ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS prev_hash VARCHAR(64);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS hash VARCHAR(64);

-- hash = SHA-256(prev_hash || conteudo canonico da linha), calculado pela aplicacao.
-- Indice para localizar o ultimo elo da cadeia e verificar integridade.
CREATE INDEX IF NOT EXISTS idx_audit_log_hash ON audit_log(hash);

CREATE OR REPLACE FUNCTION audit_log_protect() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'audit_log e append-only: UPDATE nao permitido (id=%)', OLD.id
            USING ERRCODE = 'insufficient_privilege';
    ELSIF TG_OP = 'DELETE' THEN
        IF OLD.created_at < now() - interval '10 years' THEN
            RETURN OLD;
        END IF;
        RAISE EXCEPTION 'audit_log e append-only: DELETE permitido apenas para registros com mais de 10 anos (id=%, created_at=%)',
            OLD.id, OLD.created_at
            USING ERRCODE = 'insufficient_privilege';
    ELSIF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'audit_log e append-only: TRUNCATE nao permitido'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS audit_log_no_update_delete ON audit_log;
CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_protect();

DROP TRIGGER IF EXISTS audit_log_no_truncate ON audit_log;
CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_protect();

-- data_access_log (LGPD art. 37 - registro das operacoes de tratamento): imutavel para
-- UPDATE. DELETE continua livre para a politica de retencao propria do log de acesso.
CREATE OR REPLACE FUNCTION data_access_log_protect() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'data_access_log e imutavel: UPDATE nao permitido (id=%)', OLD.id
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS data_access_log_no_update ON data_access_log;
CREATE TRIGGER data_access_log_no_update
    BEFORE UPDATE ON data_access_log
    FOR EACH ROW EXECUTE FUNCTION data_access_log_protect();
