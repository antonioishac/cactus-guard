-- V10__create_kyc_tables.sql
CREATE TABLE kyc_processes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    party_id UUID NOT NULL REFERENCES parties(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('ONBOARDING', 'PERIODIC', 'TRIGGERED')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'IN_PROGRESS', 'APPROVED', 'REJECTED', 'EXPIRED')),
    risk_level VARCHAR(20),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    assigned_to UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_kyc_processes_party_id ON kyc_processes(party_id);
CREATE INDEX idx_kyc_processes_status ON kyc_processes(status);
CREATE INDEX idx_kyc_processes_expires_at ON kyc_processes(expires_at);

CREATE TABLE kyc_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    process_id UUID NOT NULL REFERENCES kyc_processes(id) ON DELETE CASCADE,
    document_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kyc_documents_process_id ON kyc_documents(process_id);

CREATE TABLE kyc_checklist (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    process_id UUID NOT NULL REFERENCES kyc_processes(id) ON DELETE CASCADE,
    item_code VARCHAR(50) NOT NULL,
    item_description VARCHAR(255),
    required BOOLEAN NOT NULL DEFAULT true,
    completed BOOLEAN NOT NULL DEFAULT false,
    completed_at TIMESTAMP WITH TIME ZONE,
    completed_by UUID REFERENCES users(id)
);

CREATE INDEX idx_kyc_checklist_process_id ON kyc_checklist(process_id);
