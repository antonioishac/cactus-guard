-- V8__create_transaction_tables.sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    party_id UUID NOT NULL REFERENCES parties(id),
    type VARCHAR(30) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    counterparty_name VARCHAR(255),
    counterparty_document VARCHAR(20),
    channel VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_party_id ON transactions(party_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_transactions_amount ON transactions(amount);
CREATE INDEX idx_transactions_type ON transactions(type);

CREATE TABLE transaction_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    party_id UUID NOT NULL UNIQUE REFERENCES parties(id),
    expected_monthly_volume DECIMAL(18,2),
    expected_transaction_count INTEGER,
    risk_score DECIMAL(5,2),
    last_calculated TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_transaction_profiles_party_id ON transaction_profiles(party_id);
