-- V5__create_monitoring_rules.sql
CREATE TABLE monitoring_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    active BOOLEAN NOT NULL DEFAULT true,
    condition_expression TEXT,
    threshold_value DECIMAL(18,2),
    threshold_period_days INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_monitoring_rules_code ON monitoring_rules(code);
CREATE INDEX idx_monitoring_rules_active ON monitoring_rules(active);
CREATE INDEX idx_monitoring_rules_category ON monitoring_rules(category);

-- Regras iniciais alinhadas com Carta Circular 4.001
INSERT INTO monitoring_rules (code, name, category, severity, description, active) VALUES
('TXN_HIGH_VALUE', 'Transação de alto valor', 'TRANSACTIONAL', 'HIGH', 'Transação acima do limite configurado', true),
('TXN_VELOCITY', 'Velocidade anormal de transações', 'TRANSACTIONAL', 'MEDIUM', 'Múltiplas transações em curto período', true),
('PEP_MATCH', 'Match com lista PEP', 'SCREENING', 'CRITICAL', 'Parte identificada como PEP', true),
('SANCTION_MATCH', 'Match com lista de sanções', 'SCREENING', 'CRITICAL', 'Parte em lista de sanções', true),
('STRUCTURING', 'Indício de fracionamento', 'TRANSACTIONAL', 'HIGH', 'Padrão de fracionamento de valores', true),
('UNUSUAL_PATTERN', 'Padrão incomum de comportamento', 'BEHAVIORAL', 'MEDIUM', 'Desvio do perfil esperado', true);
