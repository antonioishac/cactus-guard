-- V20: Add PLD fields to parties and transactions, create addresses and relationships

-- Party PLD fields
ALTER TABLE parties ADD COLUMN IF NOT EXISTS birth_date DATE;
ALTER TABLE parties ADD COLUMN IF NOT EXISTS nationality VARCHAR(50);
ALTER TABLE parties ADD COLUMN IF NOT EXISTS occupation VARCHAR(100);

-- Transaction tracking fields
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS origin_country VARCHAR(3);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS destination_country VARCHAR(3);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS purpose VARCHAR(200);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS device_id VARCHAR(100);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);

-- Addresses table
CREATE TABLE IF NOT EXISTS party_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_id UUID NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    street VARCHAR(200),
    number VARCHAR(20),
    complement VARCHAR(100),
    neighborhood VARCHAR(100),
    city VARCHAR(100),
    state VARCHAR(50),
    zip_code VARCHAR(10),
    country VARCHAR(50) DEFAULT 'BR',
    is_primary BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_party_addresses_party ON party_addresses(party_id);

-- Relationships table
CREATE TABLE IF NOT EXISTS party_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_id UUID NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
    related_party_id UUID NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
    relationship_type VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(party_id, related_party_id, relationship_type)
);

CREATE INDEX IF NOT EXISTS idx_party_relationships_party ON party_relationships(party_id);
CREATE INDEX IF NOT EXISTS idx_party_relationships_related ON party_relationships(related_party_id);
