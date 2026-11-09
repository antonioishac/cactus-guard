-- V42: endurecimento multi-tenant - passo 1: UNIQUE (tenant_id, id) em toda tabela-pai por tenant.
-- Pre-requisito das FKs compostas (V43): a FK de uma filha precisa apontar para uma chave unica exata
-- (tenant_id, id). Descoberta dinamica em pg_constraint: pai = qualquer tabela com tenant_id referenciada por
-- FK de coluna unica -> id a partir de outra tabela com tenant_id (nenhuma relacao e esquecida).
-- Tabelas globais (roles, screening_*, high_risk_jurisdictions, report_definitions, tenants) nao tem tenant_id e ficam de fora.

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT DISTINCT c.confrelid::regclass::text AS parent, c.confrelid::regclass AS parent_oid
        FROM pg_constraint c
        JOIN pg_attribute pa ON pa.attrelid = c.confrelid AND pa.attnum = c.confkey[1]
        WHERE c.contype = 'f'
          AND c.connamespace = 'public'::regnamespace
          AND cardinality(c.conkey) = 1
          AND pa.attname = 'id'
          AND EXISTS (SELECT 1 FROM pg_attribute t WHERE t.attrelid = c.conrelid AND t.attname = 'tenant_id' AND NOT t.attisdropped)
          AND EXISTS (SELECT 1 FROM pg_attribute t WHERE t.attrelid = c.confrelid AND t.attname = 'tenant_id' AND NOT t.attisdropped)
    LOOP
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I UNIQUE (tenant_id, id)',
                       r.parent, left('uq_' || replace(r.parent, 'public.', '') || '_tenant_id', 63));
        RAISE NOTICE 'UNIQUE (tenant_id, id) criado em %', r.parent;
    END LOOP;
END $$;
