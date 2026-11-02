-- V17: Add document hash for search (encrypted field workaround)
ALTER TABLE parties ADD COLUMN IF NOT EXISTS document_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_parties_document_hash ON parties(document_hash);

-- Note: Application must set document_hash = SHA-256(document) before save
-- This allows exact match search without decrypting
