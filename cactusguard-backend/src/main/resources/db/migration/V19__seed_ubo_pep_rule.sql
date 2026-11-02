-- V19: Seed UBO_PEP rule
INSERT INTO monitoring_rules (code, name, description, severity, active, threshold_value)
VALUES ('UBO_PEP', 'UBO identificado como PEP', 'Gera alerta quando beneficiário final é marcado como PEP', 'HIGH', true, null)
ON CONFLICT (code) DO NOTHING;
