-- V6__create_screening_tables.sql
CREATE TABLE screening_lists (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL CHECK (type IN ('PEP', 'SANCTION', 'ADVERSE_MEDIA', 'INTERNAL')),
    source VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT true,
    last_updated TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE screening_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    list_id UUID NOT NULL REFERENCES screening_lists(id),
    full_name VARCHAR(255) NOT NULL,
    document VARCHAR(50),
    document_type VARCHAR(20),
    country VARCHAR(3),
    pep_category VARCHAR(50),
    pep_role VARCHAR(255),
    pep_start_date DATE,
    pep_end_date DATE,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_screening_entries_list_id ON screening_entries(list_id);
CREATE INDEX idx_screening_entries_document ON screening_entries(document);
CREATE INDEX idx_screening_entries_full_name ON screening_entries(full_name);

CREATE TABLE screening_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    party_id UUID NOT NULL REFERENCES parties(id),
    entry_id UUID REFERENCES screening_entries(id),
    list_code VARCHAR(50),
    match_score DECIMAL(5,2),
    match_type VARCHAR(30),
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'FALSE_POSITIVE', 'DISMISSED')),
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_screening_results_party_id ON screening_results(party_id);
CREATE INDEX idx_screening_results_status ON screening_results(status);

-- Listas iniciais
INSERT INTO screening_lists (code, name, type, source, active) VALUES
('PEP_BR', 'PEP Brasil', 'PEP', 'COAF / Receita', true),
('UN_SANCTIONS', 'Sanções ONU', 'SANCTION', 'UN Security Council', true),
('OFAC', 'OFAC SDN', 'SANCTION', 'US Treasury', true);
