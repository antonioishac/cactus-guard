-- V43: endurecimento multi-tenant - passo 2: FKs compostas (tenant_id, x_id) -> pai (tenant_id, id).
-- FK ignora RLS: com FK so em id, quem soubesse o UUID de uma linha de OUTRO tenant gravaria referencia
-- cruzada. A FK composta faz o proprio banco rejeitar. Converte TODA FK de coluna unica -> id entre tabelas com
-- tenant_id (descoberta em pg_constraint), preservando ON DELETE (NO ACTION/RESTRICT/CASCADE; SET NULL nao
-- existe no schema e abortaria a migration por anular tenant_id NOT NULL).
-- Colunas de FK anulaveis continuam opcionais: com MATCH SIMPLE, x_id NULL desliga a checagem (tenant_id e NOT NULL).
-- Falha se existir linha com tenant diferente do pai (dado inconsistente que precisa de correcao manual).

DO $$
DECLARE
    r RECORD;
    action_sql TEXT;
    n INT := 0;
BEGIN
    FOR r IN
        SELECT c.conname,
               c.conrelid::regclass::text AS child,
               c.confrelid::regclass::text AS parent,
               a.attname AS child_col,
               c.confdeltype AS del
        FROM pg_constraint c
        JOIN pg_attribute a  ON a.attrelid = c.conrelid  AND a.attnum = c.conkey[1]
        JOIN pg_attribute pa ON pa.attrelid = c.confrelid AND pa.attnum = c.confkey[1]
        WHERE c.contype = 'f'
          AND c.connamespace = 'public'::regnamespace
          AND cardinality(c.conkey) = 1
          AND pa.attname = 'id'
          AND EXISTS (SELECT 1 FROM pg_attribute t WHERE t.attrelid = c.conrelid AND t.attname = 'tenant_id' AND NOT t.attisdropped)
          AND EXISTS (SELECT 1 FROM pg_attribute t WHERE t.attrelid = c.confrelid AND t.attname = 'tenant_id' AND NOT t.attisdropped)
        ORDER BY 2, 4
    LOOP
        action_sql := CASE r.del
            WHEN 'a' THEN ''
            WHEN 'r' THEN ' ON DELETE RESTRICT'
            WHEN 'c' THEN ' ON DELETE CASCADE'
            ELSE NULL END;
        IF action_sql IS NULL THEN
            RAISE EXCEPTION 'ON DELETE nao suportado (%) na FK %.%', r.del, r.child, r.conname;
        END IF;

        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.child, r.conname);
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY (tenant_id, %I) REFERENCES %s (tenant_id, id)%s',
                       r.child,
                       left('fkc_' || replace(r.child, 'public.', '') || '_' || r.child_col, 63),
                       r.child_col, r.parent, action_sql);
        n := n + 1;
    END LOOP;
    RAISE NOTICE 'FKs compostas criadas: %', n;
END $$;
