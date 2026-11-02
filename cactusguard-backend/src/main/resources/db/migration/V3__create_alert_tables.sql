-- V3__create_alert_tables.sql
CREATE TABLE alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    party_id UUID REFERENCES parties(id),
    type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_ANALYSIS', 'CLOSED', 'ESCALATED', 'FALSE_POSITIVE')),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    rule_code VARCHAR(50),
    assigned_to UUID REFERENCES users(id),
    due_date TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_alerts_party_id ON alerts(party_id);
CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_alerts_severity ON alerts(severity);
CREATE INDEX idx_alerts_created_at ON alerts(created_at);
CREATE INDEX idx_alerts_assigned_to ON alerts(assigned_to);

CREATE TABLE alert_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_id UUID NOT NULL REFERENCES alerts(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20),
    performed_by UUID REFERENCES users(id),
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alert_history_alert_id ON alert_history(alert_id);
