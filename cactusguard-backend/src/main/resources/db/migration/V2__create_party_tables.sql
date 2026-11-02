-- V2__create_party_tables.sql
CREATE TABLE parties (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type VARCHAR(20) NOT NULL CHECK (type IN ('PF', 'PJ')),
    document VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(30),
    risk_level VARCHAR(20) DEFAULT 'PENDING',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_parties_document ON parties(document);
CREATE INDEX idx_parties_type ON parties(type);
CREATE INDEX idx_parties_status ON parties(status);

CREATE TABLE party_addresses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    party_id UUID NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
    street VARCHAR(255),
    number VARCHAR(20),
    complement VARCHAR(100),
    neighborhood VARCHAR(100),
    city VARCHAR(100),
    state VARCHAR(2),
    zip_code VARCHAR(10),
    country VARCHAR(50) DEFAULT 'BR',
    is_primary BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_party_addresses_party_id ON party_addresses(party_id);
