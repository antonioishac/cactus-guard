-- V15: Beneficial Owner, Risk History, Transaction Profile, Watchlist

-- Beneficial Owner (UBO)
CREATE TABLE beneficial_owners (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_id        UUID NOT NULL REFERENCES parties(id),
    owner_document  VARCHAR(20) NOT NULL,
    owner_name      VARCHAR(255) NOT NULL,
    ownership_pct   NUMERIC(5,2),
    control_type    VARCHAR(50),
    is_pep          BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_bo_party ON beneficial_owners(party_id);

-- Risk History
CREATE TABLE risk_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_id        UUID NOT NULL REFERENCES parties(id),
    previous_score  NUMERIC(5,2),
    new_score       NUMERIC(5,2) NOT NULL,
    previous_level  VARCHAR(20),
    new_level       VARCHAR(20) NOT NULL,
    reason          TEXT,
    changed_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_risk_history_party ON risk_history(party_id);

-- Transaction Profile (baseline)
CREATE TABLE transaction_profiles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_id            UUID NOT NULL REFERENCES parties(id) UNIQUE,
    avg_monthly_volume  NUMERIC(18,2),
    avg_monthly_count   INTEGER,
    max_single_amount   NUMERIC(18,2),
    typical_hours       VARCHAR(50),
    typical_counterparties INTEGER,
    last_calculated_at  TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ
);

-- Internal Watchlist
CREATE TABLE watchlist_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document        VARCHAR(20) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    reason          TEXT NOT NULL,
    severity        VARCHAR(20) NOT NULL DEFAULT 'HIGH',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    added_by        UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ
);

CREATE INDEX idx_watchlist_document ON watchlist_entries(document);
CREATE INDEX idx_watchlist_active ON watchlist_entries(active);

-- Data Access Log (LGPD)
CREATE TABLE data_access_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id),
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    fields_accessed TEXT,
    access_reason   VARCHAR(100),
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_data_access_user ON data_access_log(user_id);
CREATE INDEX idx_data_access_entity ON data_access_log(entity_type, entity_id);
