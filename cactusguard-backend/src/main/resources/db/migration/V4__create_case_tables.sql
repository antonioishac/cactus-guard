-- V4__create_case_tables.sql
CREATE TABLE cases (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_id UUID REFERENCES alerts(id),
    party_id UUID REFERENCES parties(id),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_ANALYSIS', 'PENDING_DECISION', 'APPROVED', 'REJECTED', 'ESCALATED', 'CLOSED')),
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    assigned_to UUID REFERENCES users(id),
    decision VARCHAR(20),
    decision_justification TEXT,
    decided_by UUID REFERENCES users(id),
    decided_at TIMESTAMP WITH TIME ZONE,
    sla_due_date TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_cases_alert_id ON cases(alert_id);
CREATE INDEX idx_cases_party_id ON cases(party_id);
CREATE INDEX idx_cases_status ON cases(status);
CREATE INDEX idx_cases_assigned_to ON cases(assigned_to);

CREATE TABLE case_notes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    case_id UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    author_id UUID REFERENCES users(id),
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_case_notes_case_id ON case_notes(case_id);
