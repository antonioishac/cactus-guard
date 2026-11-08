-- V27: Governanca e versionamento de regras de monitoramento
-- Regras (cenarios) devem ser aprovadas, versionadas e reconstruiveis: o auditor/BCB
-- precisa saber qual parametro estava vigente quando um alerta foi (ou nao) gerado.

ALTER TABLE monitoring_rules ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE monitoring_rules ADD COLUMN parameters JSONB;
ALTER TABLE monitoring_rules ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE monitoring_rules ADD CONSTRAINT chk_monitoring_rules_status
    CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'ACTIVE', 'INACTIVE'));
ALTER TABLE monitoring_rules ADD COLUMN carta_4001_reference VARCHAR(255);
ALTER TABLE monitoring_rules ADD COLUMN applies_to_risk_levels JSONB;   -- ["HIGH","CRITICAL"]; NULL = todos
ALTER TABLE monitoring_rules ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE monitoring_rules ADD COLUMN approved_by UUID REFERENCES users(id);
ALTER TABLE monitoring_rules ADD COLUMN approved_at TIMESTAMPTZ;
ALTER TABLE monitoring_rules ADD COLUMN effective_from TIMESTAMPTZ;
ALTER TABLE monitoring_rules ADD COLUMN effective_to TIMESTAMPTZ;
-- Adicional: regra cujo disparo gera comunicacao automatica (COE especie >= 50 mil),
-- sem passar pela analise de suspeita - o fluxo e diferente do alerta comum.
ALTER TABLE monitoring_rules ADD COLUMN auto_communication BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE monitoring_rules ADD CONSTRAINT chk_monitoring_rules_effective
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from);

UPDATE monitoring_rules
   SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'INACTIVE' END,
       effective_from = created_at;

CREATE INDEX idx_monitoring_rules_status ON monitoring_rules(status);

-- Parametros das regras existentes (espelham threshold_value/threshold_period_days)
UPDATE monitoring_rules SET parameters = '{"threshold_brl": 50000, "window_days": 1}'::jsonb
 WHERE code = 'TXN_HIGH_VALUE' AND parameters IS NULL;
UPDATE monitoring_rules SET parameters = '{"max_count": 10, "window_hours": 24}'::jsonb
 WHERE code = 'TXN_VELOCITY' AND parameters IS NULL;
UPDATE monitoring_rules SET parameters = '{"threshold_brl": 50000, "band_pct": 10, "min_occurrences": 3, "window_days": 7}'::jsonb
 WHERE code = 'STRUCTURING' AND parameters IS NULL;
UPDATE monitoring_rules SET parameters = '{"deviation_multiplier": 3.0, "baseline_months": 6}'::jsonb
 WHERE code = 'UNUSUAL_PATTERN' AND parameters IS NULL;

-- =====================================================================================
-- Versoes imutaveis (snapshot completo da regra por versao)
-- =====================================================================================
CREATE TABLE monitoring_rule_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id         UUID NOT NULL REFERENCES monitoring_rules(id),
    version         INTEGER NOT NULL,
    snapshot        JSONB NOT NULL,
    change_reason   TEXT,
    created_by      UUID REFERENCES users(id),
    approved_by     UUID REFERENCES users(id),
    approved_at     TIMESTAMPTZ,
    effective_from  TIMESTAMPTZ,
    effective_to    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rule_versions UNIQUE (rule_id, version)
);
CREATE INDEX idx_rule_versions_rule ON monitoring_rule_versions(rule_id);
CREATE INDEX idx_rule_versions_effective ON monitoring_rule_versions(rule_id, effective_from, effective_to);

-- Imutabilidade: nenhum DELETE; UPDATE apenas para encerrar a vigencia
-- (effective_to de NULL para um valor), sem alterar nenhum outro campo.
CREATE OR REPLACE FUNCTION monitoring_rule_versions_protect() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND OLD.effective_to IS NULL
       AND NEW.effective_to IS NOT NULL
       AND (to_jsonb(NEW) - 'effective_to') = (to_jsonb(OLD) - 'effective_to') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'monitoring_rule_versions e imutavel (% nao permitido, id=%)', TG_OP, OLD.id
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER monitoring_rule_versions_immutable
    BEFORE UPDATE OR DELETE ON monitoring_rule_versions
    FOR EACH ROW EXECUTE FUNCTION monitoring_rule_versions_protect();

-- =====================================================================================
-- Seeds de regras novas (cenarios Carta Circular 4.001/2020)
-- Criadas como PENDING_APPROVAL e inativas: o motor (RuleEngineService) ainda nao as
-- implementa e toda regra deve ser aprovada antes de entrar em producao. Os parametros
-- sao valores iniciais de referencia e devem ser calibrados pela instituicao.
-- =====================================================================================
INSERT INTO monitoring_rules
    (code, name, description, category, severity, active, status, threshold_value, threshold_period_days,
     parameters, carta_4001_reference, applies_to_risk_levels, auto_communication)
VALUES
('CASH_50K', 'Operação em espécie >= R$ 50 mil',
 'Depósito, saque, aporte ou provisionamento de saque em espécie igual ou superior a R$ 50.000,00. Gera comunicação automática (COE) ao COAF, independentemente de análise de suspeita.',
 'CASH', 'HIGH', false, 'PENDING_APPROVAL', 50000.00, NULL,
 '{"threshold_brl": 50000, "operations": ["DEPOSIT", "WITHDRAWAL", "CASH_CONTRIBUTION", "WITHDRAWAL_PROVISION"], "communication_type": "COE", "per_operation": true}'::jsonb,
 'Circular BCB 3.978/2020 - comunicação de operações em espécie', NULL, true),

('PEP_TRANSACTION', 'Movimentação relevante de PEP ou relacionado',
 'Operações de PEP (titular, familiar ou estreito colaborador) acima do limiar, com limiar inferior ao aplicado a clientes comuns.',
 'PEP', 'HIGH', false, 'PENDING_APPROVAL', 10000.00, 30,
 '{"threshold_brl": 10000, "window_days": 30, "include_related": true, "include_expired_within_years": 5}'::jsonb,
 'Carta Circular 4.001/2020 - situações relacionadas a pessoas expostas politicamente', NULL, false),

('PASS_THROUGH', 'Conta de passagem',
 'Entrada de recursos seguida de saída de valor equivalente em curto prazo, sem lastro econômico aparente.',
 'TRANSACTIONAL', 'HIGH', false, 'PENDING_APPROVAL', 20000.00, 2,
 '{"window_hours": 48, "min_out_in_ratio": 0.90, "min_amount_brl": 20000, "min_distinct_counterparties": 1}'::jsonb,
 'Carta Circular 4.001/2020 - movimentação incompatível / recursos em trânsito', NULL, false),

('ROUND_AMOUNTS', 'Operações recorrentes em valores redondos',
 'Recorrência de operações em valores redondos (múltiplos exatos) em curto período.',
 'TRANSACTIONAL', 'MEDIUM', false, 'PENDING_APPROVAL', 5000.00, 30,
 '{"min_amount_brl": 5000, "round_multiple": 1000, "min_occurrences": 3, "window_days": 30}'::jsonb,
 'Carta Circular 4.001/2020 - características atípicas de valores', NULL, false),

('HIGH_RISK_JURISDICTION', 'Operação com jurisdição de alto risco',
 'Operação com origem, destino ou contraparte em jurisdição listada pelo GAFI (call for action / monitoramento reforçado) ou em lista interna.',
 'GEOGRAPHIC', 'HIGH', false, 'PENDING_APPROVAL', NULL, NULL,
 '{"sources": ["GAFI_BLACK", "GAFI_GREY", "INTERNAL"], "min_amount_brl": 0, "check_fields": ["origin_country", "destination_country", "counterparty_country"]}'::jsonb,
 'Carta Circular 4.001/2020 - operações envolvendo países de alto risco (GAFI)', NULL, false),

('PROFILE_INCOMPATIBLE', 'Movimentação incompatível com renda/faturamento',
 'Volume movimentado incompatível com a renda mensal declarada (PF) ou faturamento (PJ), ou com o perfil esperado.',
 'BEHAVIORAL', 'HIGH', false, 'PENDING_APPROVAL', 10000.00, 30,
 '{"income_multiplier": 3.0, "revenue_multiplier": 1.5, "window_days": 30, "min_amount_brl": 10000, "use_expected_monthly_volume": true}'::jsonb,
 'Carta Circular 4.001/2020 - incompatibilidade com capacidade econômico-financeira', NULL, false)
ON CONFLICT (code) DO NOTHING;

-- Snapshot da versao 1 de todas as regras existentes
INSERT INTO monitoring_rule_versions (rule_id, version, snapshot, change_reason, effective_from, created_at)
SELECT r.id, r.version, to_jsonb(r), 'Snapshot inicial (V27)', r.effective_from, NOW()
FROM monitoring_rules r
ON CONFLICT (rule_id, version) DO NOTHING;
