-- V20: Add PLD fields to parties and transactions, adjust addresses and create relationships

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

-- Addresses: a tabela party_addresses ja existe desde V2. A versao original desta
-- migration usava CREATE TABLE IF NOT EXISTS, que era ignorado, e a coluna "type"
-- (NOT NULL na entity PartyAddress) nunca era criada. Correcao via ALTER TABLE.
ALTER TABLE party_addresses ADD COLUMN IF NOT EXISTS type VARCHAR(20) NOT NULL DEFAULT 'RESIDENTIAL';
ALTER TABLE party_addresses ALTER COLUMN state TYPE VARCHAR(50);
ALTER TABLE party_addresses ALTER COLUMN is_primary SET DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_party_addresses_type ON party_addresses(type);

-- Relationships table
-- created_at como TIMESTAMPTZ: a entity mapeia Instant (TIMESTAMP_WITH_TIMEZONE);
-- TIMESTAMP sem fuso falha em ddl-auto=validate.
CREATE TABLE IF NOT EXISTS party_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_id UUID NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
    related_party_id UUID NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
    relationship_type VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(party_id, related_party_id, relationship_type)
);

CREATE INDEX IF NOT EXISTS idx_party_relationships_party ON party_relationships(party_id);
CREATE INDEX IF NOT EXISTS idx_party_relationships_related ON party_relationships(related_party_id);
