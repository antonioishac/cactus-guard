-- V37: catalogo GLOBAL de regras-modelo. O provisionamento de um tenant novo copia estas linhas para
-- monitoring_rules (+ versao 1 em monitoring_rule_versions) do tenant. Sem tenant_id e sem RLS: e conteudo
-- da plataforma, mantido por migration. Semente = regras hoje existentes no tenant 'default' (com seus parametros
-- e status: as PENDING_APPROVAL continuam exigindo aprovacao de quatro olhos no tenant novo).

CREATE TABLE monitoring_rule_templates (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                   VARCHAR(50)  NOT NULL UNIQUE,
    name                   VARCHAR(255) NOT NULL,
    description            TEXT,
    category               VARCHAR(50)  NOT NULL,
    severity               VARCHAR(20)  NOT NULL,
    active                 BOOLEAN      NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    condition_expression   TEXT,
    threshold_value        NUMERIC(18,2),
    threshold_period_days  INTEGER,
    parameters             JSONB,
    carta_4001_reference   VARCHAR(255),
    applies_to_risk_levels JSONB,
    auto_communication     BOOLEAN      NOT NULL DEFAULT false,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_rule_templates_status CHECK (status IN ('ACTIVE', 'PENDING_APPROVAL', 'INACTIVE', 'DRAFT'))
);

INSERT INTO monitoring_rule_templates
    (code, name, description, category, severity, active, status, condition_expression, threshold_value,
     threshold_period_days, parameters, carta_4001_reference, applies_to_risk_levels, auto_communication)
SELECT code, name, description, category, severity, active, status, condition_expression, threshold_value,
       threshold_period_days, parameters, carta_4001_reference, applies_to_risk_levels, auto_communication
FROM monitoring_rules
WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
  AND status IN ('ACTIVE', 'PENDING_APPROVAL')
ON CONFLICT (code) DO NOTHING;
