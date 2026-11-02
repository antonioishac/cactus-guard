-- V18: Add risk_level to parties
ALTER TABLE parties ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20) DEFAULT 'LOW';
