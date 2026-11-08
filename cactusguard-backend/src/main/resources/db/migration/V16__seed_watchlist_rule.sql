-- V16: Seed WATCHLIST_MATCH rule
-- category e NOT NULL desde V5 (correcao: o insert original omitia a coluna e falhava).
INSERT INTO monitoring_rules (code, name, description, category, severity, active, threshold_value)
VALUES ('WATCHLIST_MATCH', 'Match em Watchlist Interna', 'Gera alerta quando parte está na watchlist interna', 'SCREENING', 'HIGH', true, null)
ON CONFLICT (code) DO NOTHING;
