-- V19: Seed UBO_PEP rule
-- category e NOT NULL desde V5 (correcao: o insert original omitia a coluna e falhava).
INSERT INTO monitoring_rules (code, name, description, category, severity, active, threshold_value)
VALUES ('UBO_PEP', 'UBO identificado como PEP', 'Gera alerta quando beneficiário final é marcado como PEP', 'SCREENING', 'HIGH', true, null)
ON CONFLICT (code) DO NOTHING;
