-- V50: particionamento de transactions - passo 3: monitoramento.
-- Linhas na particao DEFAULT = dados fora de faixa (faltou criar particao). O job de particoes le esta funcao e alerta
-- (log ERROR + gauge). SECURITY DEFINER porque a DEFAULT esta sob RLS FORCE e o papel de runtime nao tem tenant.
-- Devolve so contagens (nenhum dado de tenant).

CREATE OR REPLACE FUNCTION transactions_default_partition_rows() RETURNS bigint
    LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp
AS $$
DECLARE
    v_prev  text := current_setting('app.tenant_id', true);
    v_total bigint := 0;
    r       record;
BEGIN
    -- a DEFAULT esta sob RLS FORCE: conta tenant a tenant (fixando app.tenant_id so nesta transacao), sem relaxar policy
    FOR r IN SELECT id FROM tenants LOOP
        PERFORM set_config('app.tenant_id', r.id::text, true);
        v_total := v_total + (SELECT count(*) FROM ONLY transactions_default);
    END LOOP;
    PERFORM set_config('app.tenant_id', coalesce(v_prev, ''), true);
    RETURN v_total;
END $$;

-- Particoes existentes de transactions (nome, limites, linhas estimadas) - apoio a operacao/arquivamento.
CREATE OR REPLACE FUNCTION transactions_partition_info()
    RETURNS TABLE (partition_name text, bounds text, est_rows bigint)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp
AS $$
    SELECT c.relname::text, pg_get_expr(c.relpartbound, c.oid), c.reltuples::bigint
      FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
     WHERE i.inhparent = 'public.transactions'::regclass
     ORDER BY c.relname
$$;
