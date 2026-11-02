-- V9__create_siscoaf_tables.sql
CREATE TABLE siscoaf_communications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    case_id UUID REFERENCES cases(id),
    party_id UUID REFERENCES parties(id),
    type VARCHAR(30) NOT NULL CHECK (type IN ('COE', 'COS', 'COAF_COMMUNICATION')),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'SUBMITTED', 'ACKNOWLEDGED', 'REJECTED')),
    protocol_number VARCHAR(50),
    submitted_at TIMESTAMP WITH TIME ZONE,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    payload JSONB,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_siscoaf_case_id ON siscoaf_communications(case_id);
CREATE INDEX idx_siscoaf_party_id ON siscoaf_communications(party_id);
CREATE INDEX idx_siscoaf_status ON siscoaf_communications(status);
CREATE INDEX idx_siscoaf_protocol ON siscoaf_communications(protocol_number);

CREATE TABLE siscoaf_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    communication_id UUID NOT NULL REFERENCES siscoaf_communications(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20),
    performed_by UUID REFERENCES users(id),
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_siscoaf_history_comm_id ON siscoaf_history(communication_id);
