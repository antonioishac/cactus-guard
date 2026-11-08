-- V23: Campos PLD/FT de parties, beneficial_owners e party_relationships
-- Base normativa: Circular BCB 3.978/2020 (arts. 13-27 qualificacao, 19 e 27 PEP,
-- 34 aprovacao da alta administracao para PEP), Carta Circular 4.001/2020, spec 002/010/017.
-- Todos os campos novos sao NULLABLE ou possuem DEFAULT para nao afetar dados existentes.

-- =====================================================================================
-- PARTIES
-- =====================================================================================

-- Papel da parte (KYC / KYE / KYP / KYS) e origem do cadastro
ALTER TABLE parties ADD COLUMN party_role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE parties ADD CONSTRAINT chk_parties_party_role CHECK (party_role IN
    ('CUSTOMER', 'UBO', 'COUNTERPARTY', 'EMPLOYEE', 'PARTNER', 'SUPPLIER', 'REPRESENTATIVE'));

ALTER TABLE parties ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'ONBOARDED';
ALTER TABLE parties ADD CONSTRAINT chk_parties_source_type CHECK (source_type IN ('MIGRATED', 'ONBOARDED'));
-- Backfill: partes criadas pelo processo de migracao (migration_records.party_id)
UPDATE parties p SET source_type = 'MIGRATED'
WHERE EXISTS (SELECT 1 FROM migration_records mr WHERE mr.party_id = p.id);

ALTER TABLE parties ADD COLUMN relationship_start_date DATE;
ALTER TABLE parties ADD COLUMN relationship_end_date DATE;
ALTER TABLE parties ADD COLUMN onboarding_channel VARCHAR(30);

-- PEP (art. 27; status mantido por 5 anos apos deixar o cargo; art. 34 aprovacao)
ALTER TABLE parties ADD COLUMN is_pep BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE parties ADD COLUMN pep_status VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE parties ADD CONSTRAINT chk_parties_pep_status CHECK (pep_status IN ('NONE', 'ACTIVE', 'EXPIRED', 'RELATED'));
-- Adicional: categoria (NATIONAL/FOREIGN/INTERNATIONAL_ORG) - PEP estrangeiro e de
-- organismo internacional tem fontes de verificacao e fatores de risco distintos.
ALTER TABLE parties ADD COLUMN pep_category VARCHAR(30);
ALTER TABLE parties ADD COLUMN pep_role VARCHAR(255);
ALTER TABLE parties ADD COLUMN pep_start_date DATE;
ALTER TABLE parties ADD COLUMN pep_end_date DATE;
ALTER TABLE parties ADD COLUMN pep_expiry_date DATE;           -- pep_end_date + 5 anos
ALTER TABLE parties ADD COLUMN pep_approval_by UUID REFERENCES users(id);
ALTER TABLE parties ADD COLUMN pep_approval_at TIMESTAMPTZ;
ALTER TABLE parties ADD CONSTRAINT chk_parties_pep_dates
    CHECK (pep_end_date IS NULL OR pep_start_date IS NULL OR pep_end_date >= pep_start_date);

-- Risco
ALTER TABLE parties ADD COLUMN risk_score NUMERIC(5,2);
ALTER TABLE parties ADD COLUMN risk_calculated_at TIMESTAMPTZ;
ALTER TABLE parties ADD COLUMN next_review_date DATE;
ALTER TABLE parties ADD COLUMN edd_required BOOLEAN NOT NULL DEFAULT false;

-- Pessoa fisica
-- mother_name: chave essencial para desambiguar homonimos no Brasil (cruzamento com
-- Receita/listas). Gravado CRIPTOGRAFADO pela entity: e fator classico de autenticacao
-- e alvo de engenharia social; por isso VARCHAR(512).
ALTER TABLE parties ADD COLUMN mother_name VARCHAR(512);
ALTER TABLE parties ADD COLUMN country_of_residence VARCHAR(3);   -- ISO 3166-1 alpha-2
ALTER TABLE parties ADD COLUMN tax_residence_country VARCHAR(3);
ALTER TABLE parties ADD COLUMN id_document_type VARCHAR(20);      -- RG, CNH, RNE/CRNM, PASSPORT...
ALTER TABLE parties ADD COLUMN id_document_number VARCHAR(512);   -- criptografado
-- Adicional: orgao emissor/UF - necessario para validar RG (numeracao nao e nacional).
ALTER TABLE parties ADD COLUMN id_document_issuer VARCHAR(30);
ALTER TABLE parties ADD COLUMN cbo_code VARCHAR(10);
ALTER TABLE parties ADD COLUMN declared_monthly_income NUMERIC(18,2);
ALTER TABLE parties ADD COLUMN declared_net_worth NUMERIC(18,2);
ALTER TABLE parties ADD COLUMN source_of_funds VARCHAR(500);
ALTER TABLE parties ADD COLUMN source_of_wealth VARCHAR(500);
ALTER TABLE parties ADD COLUMN purpose_of_relationship VARCHAR(500);
ALTER TABLE parties ADD COLUMN expected_monthly_volume NUMERIC(18,2);

-- Pessoa juridica
ALTER TABLE parties ADD COLUMN cnae_primary VARCHAR(10);
ALTER TABLE parties ADD COLUMN cnaes_secondary JSONB;              -- ["4711-3/02", ...]
ALTER TABLE parties ADD COLUMN legal_nature_code VARCHAR(10);      -- ex.: 206-2, 399-9 (OSC)
ALTER TABLE parties ADD COLUMN incorporation_date DATE;
ALTER TABLE parties ADD COLUMN share_capital NUMERIC(18,2);
ALTER TABLE parties ADD COLUMN annual_revenue NUMERIC(18,2);
ALTER TABLE parties ADD COLUMN company_size VARCHAR(20);           -- MEI, ME, EPP, DEMAIS
ALTER TABLE parties ADD COLUMN is_nonprofit BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE parties ADD COLUMN is_publicly_traded BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE parties ADD COLUMN ubo_identified BOOLEAN;             -- NULL = nao avaliado / PF
ALTER TABLE parties ADD COLUMN ubo_not_identified_reason TEXT;

-- Situacao cadastral na Receita Federal
ALTER TABLE parties ADD COLUMN receita_status VARCHAR(20);
ALTER TABLE parties ADD CONSTRAINT chk_parties_receita_status CHECK (receita_status IS NULL OR receita_status IN
    ('REGULAR', 'SUSPENSA', 'CANCELADA', 'PENDENTE', 'NULA', 'TITULAR_FALECIDO', 'BAIXADA', 'INAPTA', 'ATIVA'));
ALTER TABLE parties ADD COLUMN receita_checked_at TIMESTAMPTZ;
ALTER TABLE parties ADD COLUMN kyc_last_update_at TIMESTAMPTZ;

-- Adicional: indisponibilidade de ativos (Lei 13.810/2019 + Res. BCB 44/2020).
-- Sancao do CSNU exige bloqueio imediato e comunicacao; o analista precisa ver na ficha
-- da parte que ha bloqueio vigente, e o motor precisa barrar movimentacao.
ALTER TABLE parties ADD COLUMN asset_freeze_active BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE parties ADD COLUMN asset_freeze_at TIMESTAMPTZ;
ALTER TABLE parties ADD COLUMN asset_freeze_reference VARCHAR(255); -- resolucao CSNU / ato

-- Indices de filtro
CREATE INDEX idx_parties_party_role ON parties(party_role);
CREATE INDEX idx_parties_source_type ON parties(source_type);
CREATE INDEX idx_parties_pep_status ON parties(pep_status) WHERE pep_status <> 'NONE';
CREATE INDEX idx_parties_is_pep ON parties(is_pep) WHERE is_pep;
CREATE INDEX idx_parties_pep_expiry ON parties(pep_expiry_date) WHERE pep_expiry_date IS NOT NULL;
CREATE INDEX idx_parties_next_review ON parties(next_review_date);
CREATE INDEX idx_parties_edd_required ON parties(edd_required) WHERE edd_required;
CREATE INDEX idx_parties_receita_status ON parties(receita_status);
CREATE INDEX idx_parties_cnae_primary ON parties(cnae_primary);
CREATE INDEX idx_parties_incorporation_date ON parties(incorporation_date);
CREATE INDEX idx_parties_asset_freeze ON parties(asset_freeze_active) WHERE asset_freeze_active;

-- =====================================================================================
-- BENEFICIAL_OWNERS
-- =====================================================================================
ALTER TABLE beneficial_owners ADD COLUMN owner_party_id UUID REFERENCES parties(id);
ALTER TABLE beneficial_owners ADD COLUMN is_final_beneficiary BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE beneficial_owners ADD COLUMN is_indirect BOOLEAN NOT NULL DEFAULT false;
-- [{"document_hash":"...","name":"Holding X","pct":60.0}, ...] do topo ate a PF final
ALTER TABLE beneficial_owners ADD COLUMN ownership_chain JSONB;
ALTER TABLE beneficial_owners ADD COLUMN nationality VARCHAR(50);
ALTER TABLE beneficial_owners ADD COLUMN verified_at TIMESTAMPTZ;
-- Adicionais:
--  owner_type: PF/PJ (cadeia societaria tem PJ intermediarias)
--  owner_document_hash: cruzamento com parties/watchlist/listas sem documento em claro
--  verified_by: quem verificou (prova de diligencia)
--  valid_from/valid_to: alteracoes societarias - reconstrucao historica do UBO na data
--  de uma operacao analisada
ALTER TABLE beneficial_owners ADD COLUMN owner_type VARCHAR(2);
ALTER TABLE beneficial_owners ADD CONSTRAINT chk_bo_owner_type CHECK (owner_type IS NULL OR owner_type IN ('PF', 'PJ'));
ALTER TABLE beneficial_owners ADD COLUMN owner_document_hash VARCHAR(64);
ALTER TABLE beneficial_owners ADD COLUMN verified_by UUID REFERENCES users(id);
ALTER TABLE beneficial_owners ADD COLUMN valid_from DATE;
ALTER TABLE beneficial_owners ADD COLUMN valid_to DATE;

CREATE INDEX idx_bo_owner_party ON beneficial_owners(owner_party_id);
CREATE INDEX idx_bo_owner_document_hash ON beneficial_owners(owner_document_hash);
CREATE INDEX idx_bo_is_pep ON beneficial_owners(is_pep) WHERE is_pep;

-- =====================================================================================
-- PARTY_RELATIONSHIPS (historico de relacionados a PEP - art. 19 / spec 017)
-- =====================================================================================
ALTER TABLE party_relationships ADD COLUMN valid_from DATE;
ALTER TABLE party_relationships ADD COLUMN valid_to DATE;
-- Adicional: details JSONB (spec 002 PEPRelationship.details: grau de parentesco,
-- fonte da informacao, % de participacao conjunta etc.)
ALTER TABLE party_relationships ADD COLUMN details JSONB;
ALTER TABLE party_relationships ADD CONSTRAINT chk_party_rel_dates
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from);
ALTER TABLE party_relationships ADD CONSTRAINT chk_party_rel_not_self
    CHECK (party_id <> related_party_id);

-- A UNIQUE(party_id, related_party_id, relationship_type) de V20 impede reabrir um
-- vinculo encerrado (perde historico). Passa a valer so para o vinculo vigente.
DO $$
DECLARE c TEXT;
BEGIN
    FOR c IN
        SELECT con.conname FROM pg_constraint con
        WHERE con.conrelid = 'party_relationships'::regclass AND con.contype = 'u'
    LOOP
        EXECUTE format('ALTER TABLE party_relationships DROP CONSTRAINT %I', c);
    END LOOP;
END $$;
CREATE UNIQUE INDEX uq_party_rel_current
    ON party_relationships(party_id, related_party_id, relationship_type)
    WHERE valid_to IS NULL;
CREATE INDEX idx_party_rel_validity ON party_relationships(valid_from, valid_to);
