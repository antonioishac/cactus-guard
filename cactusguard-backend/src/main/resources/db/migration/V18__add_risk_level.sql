-- V18: Padroniza o dominio de parties.risk_level
-- A coluna ja existe desde V2 (DEFAULT 'PENDING'); o ADD COLUMN IF NOT EXISTS e mantido
-- apenas por idempotencia. O default original desta migration ('LOW') era incorreto:
-- parte sem classificacao deve ficar PENDING ate o calculo de risco (abordagem baseada
-- em risco, Circular BCB 3.978/2020 art. 10), nunca presumida como baixo risco.
ALTER TABLE parties ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE parties ALTER COLUMN risk_level SET DEFAULT 'PENDING';

-- Normaliza eventuais valores legados (pt-BR / minusculas) antes do CHECK.
UPDATE parties SET risk_level = CASE upper(trim(risk_level))
        WHEN 'LOW'      THEN 'LOW'
        WHEN 'BAIXO'    THEN 'LOW'
        WHEN 'MEDIUM'   THEN 'MEDIUM'
        WHEN 'MEDIO'    THEN 'MEDIUM'
        WHEN 'MÉDIO'    THEN 'MEDIUM'
        WHEN 'HIGH'     THEN 'HIGH'
        WHEN 'ALTO'     THEN 'HIGH'
        WHEN 'CRITICAL' THEN 'CRITICAL'
        WHEN 'CRITICO'  THEN 'CRITICAL'
        WHEN 'CRÍTICO'  THEN 'CRITICAL'
        ELSE 'PENDING'
    END
WHERE risk_level IS NULL
   OR risk_level NOT IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL', 'PENDING');

-- Sem NOT NULL: o Hibernate insere NULL explicito quando o campo nao e setado, o que
-- ignoraria o DEFAULT. O servico deve sempre gravar um valor (PENDING ate o calculo).
ALTER TABLE parties ADD CONSTRAINT chk_parties_risk_level
    CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL', 'PENDING'));

CREATE INDEX IF NOT EXISTS idx_parties_risk_level ON parties(risk_level);
