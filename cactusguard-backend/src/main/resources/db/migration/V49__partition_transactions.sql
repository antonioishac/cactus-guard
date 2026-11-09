-- V49: particionamento de transactions - passo 2: troca para tabela PARTITION BY RANGE (transaction_date), mensal (UTC).
--
-- DECISOES (spec 047 secao 12):
--  * PK = (tenant_id, id, transaction_date): em tabela particionada toda PK/UNIQUE inclui a chave de particao.
--    O JPA continua com @Id apenas em `id` (UUID gerado pela aplicacao): o Hibernate nunca ve a PK composta.
--    Consequencia aceita: a unicidade de `id` por tenant deixa de ser garantida pelo banco entre meses distintos
--    (so UUID v4 gerado pela aplicacao; colisao e desprezivel e a PK ainda impede duplicata na mesma data).
--  * FKs das filhas: opcao (a) - as filhas (alert_transactions, siscoaf_communications, transactions.reversal_of_id)
--    ganham a coluna transaction_date (reversal_of_date) e a FK composta COMPLETA
--    (tenant_id, x_id, x_date) -> transactions(tenant_id, id, transaction_date) continua no banco. A coluna e
--    preenchida por trigger BEFORE a partir de (tenant_id, x_id), de modo que o mapeamento JPA das filhas nao muda;
--    o trigger FALHA (foreign_key_violation) se a transacao nao existir no tenant (MATCH SIMPLE com data nula
--    desligaria a FK). A data da transacao e imutavel (trigger), pois e chave da particao e das FKs.
--  * Particao DEFAULT recebe dados fora de faixa; o job alerta se ficar nao vazia (ver V50).
--  * Retencao: nenhuma particao e removida nesta fase (>= 10 anos, Circular 3.978).
--
-- Migra os dados existentes (banco de dev com linhas e banco limpo): renomeia a tabela antiga, cria a particionada,
-- cria as particoes dos meses presentes + 3 a frente, copia, confere contagem, remove a antiga e recria
-- indices, constraints, RLS (ENABLE+FORCE, mesmo padrao da V38), triggers, grants e FKs.
-- Roda como dono/BYPASSRLS (ver V38); por isso o RLS das tabelas tocadas e suspenso (NO FORCE) so durante a migracao.

-- ---------------------------------------------------------------------------------------------------------------
-- 0) funcoes de apoio das FKs compostas
-- ---------------------------------------------------------------------------------------------------------------
-- Resolve transaction_date de (tenant, id). Busca primeiro nos ultimos 120 dias (poda de particoes); se nao achar,
-- varre tudo. Nao achou = referencia inexistente no tenant => foreign_key_violation.
CREATE OR REPLACE FUNCTION resolve_transaction_date(p_tenant uuid, p_id uuid) RETURNS timestamptz
    LANGUAGE plpgsql STABLE
AS $$
DECLARE
    d timestamptz;
BEGIN
    IF p_id IS NULL THEN
        RETURN NULL;
    END IF;
    SELECT t.transaction_date INTO d FROM transactions t
     WHERE t.tenant_id = p_tenant AND t.id = p_id AND t.transaction_date >= now() - interval '120 days';
    IF d IS NULL THEN
        SELECT t.transaction_date INTO d FROM transactions t WHERE t.tenant_id = p_tenant AND t.id = p_id;
    END IF;
    IF d IS NULL THEN
        RAISE EXCEPTION 'transacao % inexistente no tenant', p_id USING ERRCODE = 'foreign_key_violation';
    END IF;
    RETURN d;
END $$;

CREATE OR REPLACE FUNCTION trg_fill_transaction_date() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.transaction_id IS NOT DISTINCT FROM OLD.transaction_id AND NEW.tenant_id = OLD.tenant_id THEN
        NEW.transaction_date := OLD.transaction_date;
    ELSE
        NEW.transaction_date := resolve_transaction_date(NEW.tenant_id, NEW.transaction_id);
    END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION trg_fill_reversal_date() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.reversal_of_id IS NOT DISTINCT FROM OLD.reversal_of_id AND NEW.tenant_id = OLD.tenant_id THEN
        NEW.reversal_of_date := OLD.reversal_of_date;
    ELSE
        NEW.reversal_of_date := resolve_transaction_date(NEW.tenant_id, NEW.reversal_of_id);
    END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION trg_transaction_date_immutable() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'transactions.transaction_date e imutavel (chave de particao e das FKs compostas)';
END $$;

-- ---------------------------------------------------------------------------------------------------------------
-- 1) desliga FKs que apontam para transactions, suspende RLS das tabelas tocadas e guarda ACL
-- ---------------------------------------------------------------------------------------------------------------
CREATE TEMP TABLE _tx_acl ON COMMIT DROP AS
    SELECT pg_get_userbyid(a.grantee) AS grantee, a.privilege_type
      FROM pg_class c, LATERAL aclexplode(c.relacl) a
     WHERE c.oid = 'public.transactions'::regclass AND a.grantee <> 0 AND a.grantee <> c.relowner;

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT conname, conrelid::regclass::text AS child, pg_get_constraintdef(oid) AS def
               FROM pg_constraint WHERE contype = 'f' AND confrelid = 'public.transactions'::regclass
    LOOP
        IF r.child NOT IN ('alert_transactions', 'siscoaf_communications', 'transactions') THEN
            RAISE EXCEPTION 'FK inesperada para transactions: %.% (%)', r.child, r.conname, r.def;
        END IF;
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.child, r.conname);
    END LOOP;
END $$;

ALTER TABLE alert_transactions NO FORCE ROW LEVEL SECURITY;
ALTER TABLE siscoaf_communications NO FORCE ROW LEVEL SECURITY;
ALTER TABLE transactions NO FORCE ROW LEVEL SECURITY;

-- ---------------------------------------------------------------------------------------------------------------
-- 2) tabela antiga -> transactions_legacy (sem constraints/indices, evita colisao de nomes)
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE transactions RENAME TO transactions_legacy;

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT conname FROM pg_constraint WHERE conrelid = 'public.transactions_legacy'::regclass ORDER BY (contype = 'p') , conname
    LOOP
        EXECUTE format('ALTER TABLE transactions_legacy DROP CONSTRAINT %I', r.conname);
    END LOOP;
    FOR r IN SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'transactions_legacy'
    LOOP
        EXECUTE format('DROP INDEX %I', r.indexname);
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------------------------------------------
-- 3) nova tabela particionada (mesmas colunas/defaults) + coluna reversal_of_date
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE transactions (LIKE transactions_legacy INCLUDING DEFAULTS) PARTITION BY RANGE (transaction_date);
ALTER TABLE transactions ADD COLUMN reversal_of_date TIMESTAMP WITH TIME ZONE;

CREATE TABLE transactions_default PARTITION OF transactions DEFAULT;

-- particoes dos meses com dados (ou o mes corrente) ate 3 meses a frente
SELECT ensure_transaction_partition_range(
    (SELECT LEAST(COALESCE(min(transaction_date AT TIME ZONE 'UTC')::date, CURRENT_DATE), CURRENT_DATE) FROM transactions_legacy),
    GREATEST((SELECT COALESCE(max(transaction_date AT TIME ZONE 'UTC')::date, CURRENT_DATE) FROM transactions_legacy), CURRENT_DATE)
        + 92);

-- ---------------------------------------------------------------------------------------------------------------
-- 4) copia (preserva ids, created_at etc.) e confere
-- ---------------------------------------------------------------------------------------------------------------
DO $$
DECLARE
    cols    text;
    lcols   text;
    n_old   bigint;
    n_new   bigint;
    n_def   bigint;
BEGIN
    SELECT string_agg(quote_ident(attname), ', ' ORDER BY attnum),
           string_agg('l.' || quote_ident(attname), ', ' ORDER BY attnum)
      INTO cols, lcols
      FROM pg_attribute
     WHERE attrelid = 'public.transactions'::regclass AND attnum > 0 AND NOT attisdropped AND attname <> 'reversal_of_date';

    EXECUTE format('INSERT INTO transactions (%s, reversal_of_date) SELECT %s, r.transaction_date FROM transactions_legacy l '
                   || 'LEFT JOIN transactions_legacy r ON r.tenant_id = l.tenant_id AND r.id = l.reversal_of_id', cols, lcols);

    SELECT count(*) INTO n_old FROM transactions_legacy;
    SELECT count(*) INTO n_new FROM transactions;
    SELECT count(*) INTO n_def FROM ONLY transactions_default;
    IF n_old <> n_new THEN
        RAISE EXCEPTION 'copia de transactions divergente: legacy=% nova=%', n_old, n_new;
    END IF;
    IF n_def <> 0 THEN
        RAISE EXCEPTION 'linhas na particao DEFAULT apos a migracao: %', n_def;
    END IF;
    RAISE NOTICE 'transactions particionada: % linha(s) migradas', n_new;
END $$;

-- RLS da DEFAULT (ENABLE+FORCE+policy, como toda particao). Depois da conferencia acima, que le a DEFAULT como dono.
ALTER TABLE transactions_default ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions_default FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON transactions_default
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

-- ---------------------------------------------------------------------------------------------------------------
-- 5) constraints e indices (propagam para todas as particoes, atuais e futuras)
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE transactions ADD CONSTRAINT transactions_pkey PRIMARY KEY (tenant_id, id, transaction_date);
ALTER TABLE transactions ADD CONSTRAINT chk_transactions_direction CHECK (direction IS NULL OR direction IN ('IN', 'OUT'));
ALTER TABLE transactions ADD CONSTRAINT chk_transactions_payment_method CHECK (payment_method IS NULL OR payment_method IN
    ('PIX', 'TED', 'DOC', 'BOLETO', 'CASH', 'CARD', 'FX', 'CRYPTO', 'INTERNAL'));
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
ALTER TABLE transactions ADD CONSTRAINT fkc_transactions_party_id
    FOREIGN KEY (tenant_id, party_id) REFERENCES parties (tenant_id, id);
ALTER TABLE transactions ADD CONSTRAINT fkc_transactions_counterparty_party_id
    FOREIGN KEY (tenant_id, counterparty_party_id) REFERENCES parties (tenant_id, id);
ALTER TABLE transactions ADD CONSTRAINT fkc_transactions_reversal_of_id
    FOREIGN KEY (tenant_id, reversal_of_id, reversal_of_date) REFERENCES transactions (tenant_id, id, transaction_date);

-- Padrao de acesso: motor de regras (tenant, parte, janela) e varreduras em lote (tenant, janela). Removidos por baixa
-- seletividade/redundancia: idx por type, amount, payment_method, cp_country, party_id, transaction_date isolados e
-- idx_transactions_tenant (prefixo de qualquer indice composto). Cada indice e replicado em TODA particao (custo de escrita).
CREATE INDEX idx_transactions_tenant_party_date ON transactions (tenant_id, party_id, transaction_date);
CREATE INDEX idx_transactions_tenant_date ON transactions (tenant_id, transaction_date);
CREATE INDEX idx_transactions_cash_window ON transactions (tenant_id, transaction_date)
    WHERE is_cash OR cash_withdrawal_provision;
CREATE INDEX idx_transactions_e2e ON transactions (tenant_id, end_to_end_id) WHERE end_to_end_id IS NOT NULL;
CREATE INDEX idx_transactions_external_ref ON transactions (tenant_id, external_reference) WHERE external_reference IS NOT NULL;
CREATE INDEX idx_transactions_cp_doc_hash ON transactions (tenant_id, counterparty_document_hash, transaction_date)
    WHERE counterparty_document_hash IS NOT NULL;
CREATE INDEX idx_transactions_cp_party ON transactions (tenant_id, counterparty_party_id) WHERE counterparty_party_id IS NOT NULL;
CREATE INDEX idx_transactions_reversal ON transactions (tenant_id, reversal_of_id) WHERE reversal_of_id IS NOT NULL;
CREATE INDEX idx_transactions_source_batch ON transactions (source_batch_id) WHERE source_batch_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------------------------------
-- 6) triggers, RLS (padrao V38) e grants
-- ---------------------------------------------------------------------------------------------------------------
CREATE TRIGGER trg_transactions_reversal_date BEFORE INSERT OR UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION trg_fill_reversal_date();
CREATE TRIGGER trg_transactions_date_immutable BEFORE UPDATE ON transactions
    FOR EACH ROW WHEN (OLD.transaction_date IS DISTINCT FROM NEW.transaction_date)
    EXECUTE FUNCTION trg_transaction_date_immutable();

ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON transactions
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT DISTINCT grantee, privilege_type FROM _tx_acl LOOP
        EXECUTE format('GRANT %s ON transactions TO %I', r.privilege_type, r.grantee);
    END LOOP;
    -- particoes ja criadas (ensure_*) nasceram antes do GRANT da mae: replica os privilegios
    FOR r IN SELECT c.relname, a.grantee, a.privilege_type FROM (
                 SELECT DISTINCT grantee, privilege_type FROM _tx_acl) a,
             (SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
               WHERE i.inhparent = 'public.transactions'::regclass) c
    LOOP
        EXECUTE format('GRANT %s ON %I TO %I', r.privilege_type, r.relname, r.grantee);
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------------------------------------------
-- 7) filhas: transaction_date + trigger + FK composta completa
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE alert_transactions ADD COLUMN transaction_date TIMESTAMP WITH TIME ZONE;
UPDATE alert_transactions a SET transaction_date = t.transaction_date
  FROM transactions t WHERE t.tenant_id = a.tenant_id AND t.id = a.transaction_id;
ALTER TABLE alert_transactions ALTER COLUMN transaction_date SET NOT NULL;
ALTER TABLE alert_transactions ADD CONSTRAINT fkc_alert_transactions_transaction_id
    FOREIGN KEY (tenant_id, transaction_id, transaction_date) REFERENCES transactions (tenant_id, id, transaction_date);
CREATE TRIGGER trg_alert_transactions_date BEFORE INSERT OR UPDATE ON alert_transactions
    FOR EACH ROW EXECUTE FUNCTION trg_fill_transaction_date();

ALTER TABLE siscoaf_communications ADD COLUMN transaction_date TIMESTAMP WITH TIME ZONE;
UPDATE siscoaf_communications s SET transaction_date = t.transaction_date
  FROM transactions t WHERE s.transaction_id IS NOT NULL AND t.tenant_id = s.tenant_id AND t.id = s.transaction_id;
ALTER TABLE siscoaf_communications ADD CONSTRAINT fkc_siscoaf_communications_transaction_id
    FOREIGN KEY (tenant_id, transaction_id, transaction_date) REFERENCES transactions (tenant_id, id, transaction_date);
CREATE TRIGGER trg_siscoaf_communications_date BEFORE INSERT OR UPDATE ON siscoaf_communications
    FOR EACH ROW EXECUTE FUNCTION trg_fill_transaction_date();

ALTER TABLE alert_transactions FORCE ROW LEVEL SECURITY;
ALTER TABLE siscoaf_communications FORCE ROW LEVEL SECURITY;

-- ---------------------------------------------------------------------------------------------------------------
-- 8) remove a tabela antiga (copia conferida acima, na mesma transacao) e atualiza estatisticas
-- ---------------------------------------------------------------------------------------------------------------
DROP TABLE transactions_legacy;
ANALYZE transactions;
