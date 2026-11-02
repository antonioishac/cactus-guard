-- V16: Seed WATCHLIST_MATCH rule
INSERT INTO monitoring_rules (code, name, description, severity, active, threshold_value)
VALUES ('WATCHLIST_MATCH', 'Match em Watchlist Interna', 'Gera alerta quando parte está na watchlist interna', 'HIGH', true, null)
ON CONFLICT (code) DO NOTHING;
