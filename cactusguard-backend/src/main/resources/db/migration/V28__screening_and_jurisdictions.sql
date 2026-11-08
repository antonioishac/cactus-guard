-- V28: Screening (CSNU/GAFI) e jurisdicoes de alto risco

-- =====================================================================================
-- SCREENING_LISTS
-- =====================================================================================
ALTER TABLE screening_lists DROP CONSTRAINT IF EXISTS screening_lists_type_check;
ALTER TABLE screening_lists ADD CONSTRAINT screening_lists_type_check
    CHECK (type IN ('PEP', 'SANCTION', 'CSNU', 'HIGH_RISK_JURISDICTION', 'ADVERSE_MEDIA', 'INTERNAL'));

-- Adicional: listas cujo match exige indisponibilidade imediata de ativos
-- (Lei 13.810/2019 + Res. BCB 44/2020 - sancoes do Conselho de Seguranca da ONU).
-- O fluxo de um match nessas listas e diferente (bloqueio + comunicacao), nao apenas alerta.
ALTER TABLE screening_lists ADD COLUMN requires_asset_freeze BOOLEAN NOT NULL DEFAULT false;
UPDATE screening_lists SET requires_asset_freeze = true WHERE code = 'UN_SANCTIONS';

INSERT INTO screening_lists (code, name, type, source, active, requires_asset_freeze) VALUES
('CSNU', 'Sanções do Conselho de Segurança da ONU (Lei 13.810/2019)', 'CSNU', 'UN Security Council Consolidated List', true, true),
('GAFI_BLACK', 'GAFI - Jurisdições sujeitas a chamada à ação', 'HIGH_RISK_JURISDICTION', 'FATF/GAFI', true, false),
('GAFI_GREY', 'GAFI - Jurisdições sob monitoramento reforçado', 'HIGH_RISK_JURISDICTION', 'FATF/GAFI', true, false)
ON CONFLICT (code) DO NOTHING;

-- =====================================================================================
-- SCREENING_RESULTS
-- =====================================================================================
ALTER TABLE screening_results ADD COLUMN list_type VARCHAR(30);
ALTER TABLE screening_results ADD COLUMN matched_name VARCHAR(255);
ALTER TABLE screening_results ADD COLUMN details JSONB;
ALTER TABLE screening_results ADD COLUMN review_justification TEXT;
ALTER TABLE screening_results ADD COLUMN next_due_at TIMESTAMPTZ;

UPDATE screening_results sr SET list_type = sl.type
FROM screening_lists sl WHERE sl.code = sr.list_code AND sr.list_type IS NULL;

CREATE INDEX idx_screening_results_list_type ON screening_results(list_type);
CREATE INDEX idx_screening_results_next_due ON screening_results(next_due_at);
CREATE INDEX idx_screening_results_entry ON screening_results(entry_id);

-- =====================================================================================
-- HIGH_RISK_JURISDICTIONS
-- =====================================================================================
CREATE TABLE high_risk_jurisdictions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code    VARCHAR(3) NOT NULL,          -- ISO 3166-1 alpha-2
    country_name    VARCHAR(100),
    source          VARCHAR(20) NOT NULL,
    valid_from      DATE NOT NULL,
    valid_to        DATE,
    notes           TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- TAX_HAVEN (adicional): paises de tributacao favorecida (IN RFB 1.037/2010) sao
    -- fator de risco recorrente nos cenarios de PLD, distinto das listas GAFI.
    CONSTRAINT chk_hrj_source CHECK (source IN ('GAFI_BLACK', 'GAFI_GREY', 'INTERNAL', 'TAX_HAVEN')),
    CONSTRAINT chk_hrj_validity CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX idx_hrj_country ON high_risk_jurisdictions(country_code);
CREATE INDEX idx_hrj_source ON high_risk_jurisdictions(source);
-- Uma entrada vigente por pais/fonte
CREATE UNIQUE INDEX uq_hrj_current ON high_risk_jurisdictions(country_code, source) WHERE valid_to IS NULL;

-- Seed: jurisdicoes sujeitas a "call for action" do GAFI (lista estavel ha anos).
-- A lista cinza muda a cada plenaria (fev/jun/out) e NAO e semeada aqui: deve ser
-- carregada/atualizada por processo operacional com a publicacao vigente do GAFI.
INSERT INTO high_risk_jurisdictions (country_code, country_name, source, valid_from, notes) VALUES
('KP', 'Coreia do Norte', 'GAFI_BLACK', DATE '2011-02-25', 'FATF call for action - conferir publicação vigente'),
('IR', 'Irã',             'GAFI_BLACK', DATE '2009-02-25', 'FATF call for action - conferir publicação vigente'),
('MM', 'Mianmar',         'GAFI_BLACK', DATE '2022-10-21', 'FATF call for action - conferir publicação vigente');
