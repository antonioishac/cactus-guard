-- V26: Campos PLD/FT de siscoaf_communications
-- Tipos:
--   COS                  - comunicacao de operacao suspeita (apos analise)
--   COE                  - comunicacao automatica de operacao em especie >= R$ 50.000,00
--                          (independe de analise de suspeita)
--   NEGATIVE_DECLARATION - declaracao anual de nao ocorrencia (reference_year)
--   COAF_COMMUNICATION   - legado (V9)
--   SAR                  - legado: gravado hoje pelo SarGeneratorService; equivale a COS.

ALTER TABLE siscoaf_communications DROP CONSTRAINT IF EXISTS siscoaf_communications_type_check;
ALTER TABLE siscoaf_communications ADD CONSTRAINT siscoaf_communications_type_check
    CHECK (type IN ('COS', 'COE', 'NEGATIVE_DECLARATION', 'COAF_COMMUNICATION', 'SAR'));

ALTER TABLE siscoaf_communications DROP CONSTRAINT IF EXISTS siscoaf_communications_status_check;
ALTER TABLE siscoaf_communications ADD CONSTRAINT siscoaf_communications_status_check
    CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'SUBMITTED', 'ACKNOWLEDGED',
                      'REJECTED', 'CANCELLED', 'RECTIFIED'));

ALTER TABLE siscoaf_communications ADD COLUMN deadline_at TIMESTAMPTZ;   -- dia util seguinte a decided_at
ALTER TABLE siscoaf_communications ADD COLUMN decided_at TIMESTAMPTZ;
ALTER TABLE siscoaf_communications ADD COLUMN reference_year INTEGER;    -- declaracao negativa
ALTER TABLE siscoaf_communications ADD COLUMN amount NUMERIC(18,2);
ALTER TABLE siscoaf_communications ADD COLUMN indicator_codes JSONB;
ALTER TABLE siscoaf_communications ADD COLUMN original_communication_id UUID REFERENCES siscoaf_communications(id);
ALTER TABLE siscoaf_communications ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE siscoaf_communications ADD COLUMN cancel_reason TEXT;
-- Adicionais:
--  transaction_id: COE refere-se a uma operacao em especie especifica; permite garantir
--    que a mesma operacao nao seja comunicada duas vezes.
--  approved_by/approved_at: quem autorizou o envio (diretor responsavel / delegacao).
ALTER TABLE siscoaf_communications ADD COLUMN transaction_id UUID REFERENCES transactions(id);
ALTER TABLE siscoaf_communications ADD COLUMN approved_by UUID REFERENCES users(id);
ALTER TABLE siscoaf_communications ADD COLUMN approved_at TIMESTAMPTZ;

ALTER TABLE siscoaf_communications ADD CONSTRAINT chk_siscoaf_reference_year
    CHECK (type <> 'NEGATIVE_DECLARATION' OR reference_year IS NOT NULL);
ALTER TABLE siscoaf_communications ADD CONSTRAINT chk_siscoaf_not_self_rectify
    CHECK (original_communication_id IS NULL OR original_communication_id <> id);

CREATE INDEX idx_siscoaf_type ON siscoaf_communications(type);
CREATE INDEX idx_siscoaf_deadline ON siscoaf_communications(deadline_at)
    WHERE status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED');
CREATE INDEX idx_siscoaf_original ON siscoaf_communications(original_communication_id);
CREATE INDEX idx_siscoaf_transaction ON siscoaf_communications(transaction_id);
-- Uma declaracao negativa vigente por ano
CREATE UNIQUE INDEX uq_siscoaf_negative_year ON siscoaf_communications(reference_year)
    WHERE type = 'NEGATIVE_DECLARATION' AND status NOT IN ('CANCELLED', 'RECTIFIED', 'REJECTED');
-- Uma COE vigente por operacao
CREATE UNIQUE INDEX uq_siscoaf_coe_transaction ON siscoaf_communications(transaction_id)
    WHERE type = 'COE' AND transaction_id IS NOT NULL AND status NOT IN ('CANCELLED', 'RECTIFIED', 'REJECTED');
