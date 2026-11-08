-- V25: Campos PLD/FT de alerts e cases + alert_transactions + case_evidences
-- Prazos (Circular 3.978 art. 43/spec 003): selecao ate 45 dias da ocorrencia;
-- analise em dossie ate 45 dias da selecao; comunicacao ao COAF no dia util seguinte
-- a decisao de comunicar.

-- =====================================================================================
-- ALERTS
-- =====================================================================================
ALTER TABLE alerts ADD COLUMN occurred_at TIMESTAMPTZ;          -- data da operacao (inicia os 45 dias)
ALTER TABLE alerts ADD COLUMN selection_deadline TIMESTAMPTZ;   -- occurred_at + 45 dias
ALTER TABLE alerts ADD COLUMN rule_id UUID REFERENCES monitoring_rules(id);
ALTER TABLE alerts ADD COLUMN rule_version INTEGER;
ALTER TABLE alerts ADD COLUMN score NUMERIC(5,2);
ALTER TABLE alerts ADD COLUMN total_amount NUMERIC(18,2);
ALTER TABLE alerts ADD COLUMN dedup_key VARCHAR(255);
ALTER TABLE alerts ADD COLUMN closed_by UUID REFERENCES users(id);
ALTER TABLE alerts ADD COLUMN closure_reason VARCHAR(50);       -- FALSE_POSITIVE, CASE_OPENED, DUPLICATE, ...
-- Adicional: justificativa textual do encerramento (exigida para arquivamento; a
-- classificacao em closure_reason sozinha nao prova a analise).
ALTER TABLE alerts ADD COLUMN closure_justification TEXT;
-- Taxonomia de falso positivo - alimenta a calibracao das regras (spec 013).
ALTER TABLE alerts ADD COLUMN false_positive_reason VARCHAR(100);
-- Adicional: snapshot dos parametros/metricas que dispararam o alerta (limiar, valor
-- observado, janela). Permite ao analista e ao auditor entender o disparo mesmo apos
-- a regra mudar de versao.
ALTER TABLE alerts ADD COLUMN evidence JSONB;

CREATE INDEX idx_alerts_occurred_at ON alerts(occurred_at);
CREATE INDEX idx_alerts_selection_deadline ON alerts(selection_deadline)
    WHERE status IN ('OPEN', 'IN_ANALYSIS', 'ESCALATED');
CREATE INDEX idx_alerts_rule_id ON alerts(rule_id);
CREATE INDEX idx_alerts_rule_code ON alerts(rule_code);
-- Deduplicacao: no maximo um alerta aberto por chave (parte+regra+janela, definido pelo motor)
CREATE UNIQUE INDEX uq_alerts_dedup_open ON alerts(dedup_key)
    WHERE dedup_key IS NOT NULL AND status IN ('OPEN', 'IN_ANALYSIS', 'ESCALATED');

-- Transacoes que geraram o alerta (sem ON DELETE CASCADE: retencao de 10 anos)
CREATE TABLE alert_transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id        UUID NOT NULL REFERENCES alerts(id),
    transaction_id  UUID NOT NULL REFERENCES transactions(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_alert_transactions UNIQUE (alert_id, transaction_id)
);
CREATE INDEX idx_alert_transactions_transaction ON alert_transactions(transaction_id);

-- =====================================================================================
-- CASES
-- =====================================================================================
ALTER TABLE cases ADD COLUMN selected_at TIMESTAMPTZ;
ALTER TABLE cases ADD COLUMN analysis_deadline TIMESTAMPTZ;      -- selected_at + 45 dias
ALTER TABLE cases ADD COLUMN communication_deadline TIMESTAMPTZ; -- dia util seguinte a decisao
ALTER TABLE cases ADD COLUMN escalation_level INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cases ADD COLUMN escalated_to UUID REFERENCES users(id);
ALTER TABLE cases ADD COLUMN escalated_at TIMESTAMPTZ;
ALTER TABLE cases ADD COLUMN is_edd BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE cases ADD COLUMN approved_by UUID REFERENCES users(id);  -- quatro olhos
ALTER TABLE cases ADD COLUMN approved_at TIMESTAMPTZ;
ALTER TABLE cases ADD COLUMN indicator_codes JSONB;              -- indicios Carta Circular 4.001
ALTER TABLE cases ADD COLUMN total_amount_involved NUMERIC(18,2);
ALTER TABLE cases ADD COLUMN period_start DATE;
ALTER TABLE cases ADD COLUMN period_end DATE;
ALTER TABLE cases ADD COLUMN dossier_summary TEXT;
-- Adicionais:
--  occurred_at: data da operacao mais antiga do caso - prova de que selected_at
--    respeitou os 45 dias quando o caso agrega varios alertas.
--  closed_at: spec 002 (Case.closed_at); hoje so existe decided_at.
--  case_number: identificador legivel (ex.: CG-2026-000123) para dossie, comite e
--    referencia cruzada com a comunicacao ao COAF.
ALTER TABLE cases ADD COLUMN occurred_at TIMESTAMPTZ;
ALTER TABLE cases ADD COLUMN closed_at TIMESTAMPTZ;
ALTER TABLE cases ADD COLUMN case_number VARCHAR(30);
CREATE UNIQUE INDEX uq_cases_case_number ON cases(case_number) WHERE case_number IS NOT NULL;

ALTER TABLE cases ADD CONSTRAINT chk_cases_period CHECK (period_end IS NULL OR period_start IS NULL OR period_end >= period_start);
-- Quatro olhos: quem aprova nao pode ser quem decidiu
ALTER TABLE cases ADD CONSTRAINT chk_cases_four_eyes CHECK (approved_by IS NULL OR decided_by IS NULL OR approved_by <> decided_by);

ALTER TABLE cases DROP CONSTRAINT IF EXISTS cases_status_check;
ALTER TABLE cases ADD CONSTRAINT cases_status_check CHECK (status IN (
    'OPEN', 'IN_ANALYSIS', 'PENDING_DECISION', 'PENDING_APPROVAL',
    'APPROVED', 'REJECTED', 'ESCALATED',
    'COMMUNICATE', 'ARCHIVE', 'COMMUNICATED', 'ARCHIVED',
    'CLOSED'));

CREATE INDEX idx_cases_analysis_deadline ON cases(analysis_deadline);
CREATE INDEX idx_cases_communication_deadline ON cases(communication_deadline) WHERE communication_deadline IS NOT NULL;
CREATE INDEX idx_cases_is_edd ON cases(is_edd) WHERE is_edd;
CREATE INDEX idx_cases_escalated_to ON cases(escalated_to);
CREATE INDEX idx_cases_decision ON cases(decision);

-- Evidencias do dossie (imutaveis; sha256 prova integridade)
CREATE TABLE case_evidences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         UUID NOT NULL REFERENCES cases(id),
    file_name       VARCHAR(255) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    content_type    VARCHAR(100),
    sha256          VARCHAR(64) NOT NULL,
    size_bytes      BIGINT,
    description     TEXT,
    uploaded_by     UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_case_evidences_case ON case_evidences(case_id);
CREATE INDEX idx_case_evidences_sha256 ON case_evidences(sha256);
