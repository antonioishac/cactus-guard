-- V31: numeracao legivel de cases (CG-AAAA-NNNNNN), sequencial por ano.
-- Contador por ano com lock pessimista na aplicacao (sem lacunas por concorrencia; lacunas
-- apenas se a transacao do case for revertida). case_number ja possui indice unico (V25).

CREATE TABLE case_number_counters (
    counter_year INTEGER PRIMARY KEY,
    last_value   BIGINT  NOT NULL DEFAULT 0
);

-- Backfill: cases existentes sem numero recebem sequencia por ano de criacao (ordem de criacao)
WITH base AS (
    SELECT EXTRACT(YEAR FROM created_at)::int AS y,
           COALESCE(MAX(SUBSTRING(case_number FROM 9)::bigint), 0) AS mx
      FROM cases
     WHERE case_number ~ '^CG-[0-9]{4}-[0-9]{6}$'
     GROUP BY 1
), numbered AS (
    SELECT c.id,
           EXTRACT(YEAR FROM c.created_at)::int AS y,
           ROW_NUMBER() OVER (PARTITION BY EXTRACT(YEAR FROM c.created_at) ORDER BY c.created_at, c.id) AS rn
      FROM cases c
     WHERE c.case_number IS NULL
)
UPDATE cases c
   SET case_number = 'CG-' || n.y || '-' || LPAD((n.rn + COALESCE(b.mx, 0))::text, 6, '0')
  FROM numbered n LEFT JOIN base b ON b.y = n.y
 WHERE c.id = n.id;

INSERT INTO case_number_counters (counter_year, last_value)
SELECT SUBSTRING(case_number FROM 4 FOR 4)::int, MAX(SUBSTRING(case_number FROM 9)::bigint)
  FROM cases
 WHERE case_number ~ '^CG-[0-9]{4}-[0-9]{6}$'
 GROUP BY 1;
