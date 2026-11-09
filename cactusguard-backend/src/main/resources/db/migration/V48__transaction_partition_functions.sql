-- V48: particionamento de transactions - passo 1: funcoes de gestao de particoes (spec 047 secao 12, spec 041).
--
-- Esta migration NAO altera nenhuma tabela. Cria as funcoes usadas pela V49 (troca para tabela particionada) e pelo
-- job TransactionPartitionJob (criacao antecipada mensal). Particao = 1 mes em UTC (limites explicitos em UTC,
-- independentes do fuso da sessao): transactions_pAAAAMM.
--
-- SECURITY DEFINER: o papel de runtime da aplicacao (NOSUPERUSER, nao-dono) nao pode executar CREATE TABLE ...
-- PARTITION OF; a funcao roda como o dono das tabelas (papel de migration). search_path fixo contra sequestro.
-- Particoes nascem com RLS ENABLE + FORCE + policy tenant_isolation identica a da mae (consulta DIRETA a uma particao
-- usa o RLS da propria particao, nao o da mae) e recebem os mesmos GRANTs da mae. Como FORCE tambem alcanca o dono, as
-- funcoes que precisam ler a DEFAULT (mover linhas, contar) iteram os tenants fixando app.tenant_id (transacao local) -
-- nenhuma policy e relaxada. Nenhuma funcao remove particao: retencao >= 10 anos.

CREATE OR REPLACE FUNCTION ensure_transaction_partition(p_month date) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp
AS $$
DECLARE
    v_first   date := p_month - (extract(day FROM p_month)::int - 1);
    v_lo      timestamptz := v_first::timestamp AT TIME ZONE 'UTC';
    v_hi      timestamptz := (v_first + interval '1 month')::date::timestamp AT TIME ZONE 'UTC';
    v_name    text := 'transactions_p' || to_char(v_first, 'YYYYMM');
    v_moved   bigint := 0;
    v_n       bigint;
    v_prev    text := current_setting('app.tenant_id', true);
    r         record;
    g         record;
BEGIN
    IF to_regclass('public.transactions') IS NULL THEN
        RAISE EXCEPTION 'tabela transactions inexistente';
    END IF;
    -- serializa criadores concorrentes (varias instancias da aplicacao)
    PERFORM pg_advisory_xact_lock(hashtext('ensure_transaction_partition'));

    IF to_regclass('public.' || quote_ident(v_name)) IS NOT NULL THEN
        RETURN false;
    END IF;

    IF to_regclass('public.transactions_default') IS NOT NULL THEN
        -- linhas que ja caíram na DEFAULT e pertencem a este mes precisam migrar para a nova particao,
        -- senao o ATTACH/CREATE falha ("updated partition constraint for default partition would be violated").
        FOR r IN SELECT id FROM tenants LOOP
            PERFORM set_config('app.tenant_id', r.id::text, true);
            SELECT v_moved + count(*) INTO v_moved FROM ONLY transactions_default
             WHERE transaction_date >= v_lo AND transaction_date < v_hi;
        END LOOP;
        IF v_moved > 0 THEN
            v_moved := 0;
            EXECUTE format('CREATE TABLE %I (LIKE transactions INCLUDING DEFAULTS INCLUDING CONSTRAINTS)', v_name);
            FOR r IN SELECT id FROM tenants LOOP
                PERFORM set_config('app.tenant_id', r.id::text, true);
                EXECUTE format('WITH m AS (DELETE FROM ONLY transactions_default WHERE transaction_date >= %L AND transaction_date < %L RETURNING *) '
                               || 'INSERT INTO %I SELECT * FROM m', v_lo, v_hi, v_name);
                GET DIAGNOSTICS v_n = ROW_COUNT;
                v_moved := v_moved + v_n;
            END LOOP;
            EXECUTE format('ALTER TABLE transactions ATTACH PARTITION %I FOR VALUES FROM (%L) TO (%L)', v_name, v_lo, v_hi);
            RAISE WARNING 'particao % criada movendo % linha(s) da DEFAULT (dados fora de faixa)', v_name, v_moved;
        END IF;
        PERFORM set_config('app.tenant_id', coalesce(v_prev, ''), true);
    END IF;

    IF to_regclass('public.' || quote_ident(v_name)) IS NULL THEN
        EXECUTE format('CREATE TABLE %I PARTITION OF transactions FOR VALUES FROM (%L) TO (%L)', v_name, v_lo, v_hi);
    END IF;

    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', v_name);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', v_name);
    EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', v_name);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (tenant_id = (SELECT app_current_tenant())) '
                   || 'WITH CHECK (tenant_id = (SELECT app_current_tenant()))', v_name);
    -- mesmos privilegios da tabela-mae (exceto o dono)
    FOR g IN SELECT pg_get_userbyid(a.grantee) AS grantee, a.privilege_type
               FROM pg_class c, LATERAL aclexplode(c.relacl) a
              WHERE c.oid = 'public.transactions'::regclass AND a.grantee <> 0 AND a.grantee <> c.relowner
    LOOP
        EXECUTE format('GRANT %s ON %I TO %I', g.privilege_type, v_name, g.grantee);
    END LOOP;
    RETURN true;
END $$;

-- Cria (se faltar) as particoes de [mes corrente - p_months_back, mes corrente + p_months_ahead]. Idempotente.
-- Devolve quantas particoes foram criadas. Limites defensivos (a funcao e chamavel pelo papel de runtime).
CREATE OR REPLACE FUNCTION ensure_transaction_partitions(p_months_ahead int DEFAULT 3, p_months_back int DEFAULT 0) RETURNS int
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp
AS $$
DECLARE
    v_today   date := (now() AT TIME ZONE 'UTC')::date;
    v_created int := 0;
    i         int;
BEGIN
    IF p_months_ahead < 0 OR p_months_ahead > 36 OR p_months_back < 0 OR p_months_back > 24 THEN
        RAISE EXCEPTION 'intervalo de particoes invalido (ahead 0..36, back 0..24)';
    END IF;
    FOR i IN -p_months_back..p_months_ahead LOOP
        IF ensure_transaction_partition((v_today + make_interval(months => i))::date) THEN
            v_created := v_created + 1;
        END IF;
    END LOOP;
    RETURN v_created;
END $$;

-- Uso interno da migration V49 (migracao de dados historicos): cria particoes para um intervalo de datas.
CREATE OR REPLACE FUNCTION ensure_transaction_partition_range(p_from date, p_to date) RETURNS int
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp
AS $$
DECLARE
    v_cur     date := p_from - (extract(day FROM p_from)::int - 1);
    v_created int := 0;
BEGIN
    IF p_to - p_from > 366 * 40 THEN
        RAISE EXCEPTION 'intervalo de particoes excessivo';
    END IF;
    WHILE v_cur <= p_to LOOP
        IF ensure_transaction_partition(v_cur) THEN
            v_created := v_created + 1;
        END IF;
        v_cur := (v_cur + interval '1 month')::date;
    END LOOP;
    RETURN v_created;
END $$;
