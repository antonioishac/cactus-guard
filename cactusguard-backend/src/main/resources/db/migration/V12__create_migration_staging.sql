-- V12__create_migration_staging.sql
CREATE TABLE migration_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_system VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'PARTIAL')),
    total_records INTEGER DEFAULT 0,
    processed_records INTEGER DEFAULT 0,
    failed_records INTEGER DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE migration_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    batch_id UUID NOT NULL REFERENCES migration_batches(id),
    external_id VARCHAR(100),
    document VARCHAR(20),
    raw_data JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED', 'SKIPPED')),
    party_id UUID REFERENCES parties(id),
    error_message TEXT,
    processed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_migration_records_batch_id ON migration_records(batch_id);
CREATE INDEX idx_migration_records_status ON migration_records(status);
CREATE INDEX idx_migration_records_document ON migration_records(document);
