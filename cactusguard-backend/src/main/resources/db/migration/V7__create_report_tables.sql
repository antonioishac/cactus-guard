-- V7__create_report_tables.sql
CREATE TABLE report_definitions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE report_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    report_code VARCHAR(50) NOT NULL,
    requested_by UUID REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    format VARCHAR(10) NOT NULL CHECK (format IN ('PDF', 'EXCEL')),
    parameters JSONB,
    file_path VARCHAR(500),
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_report_jobs_status ON report_jobs(status);
CREATE INDEX idx_report_jobs_requested_by ON report_jobs(requested_by);

INSERT INTO report_definitions (code, name, category, description) VALUES
('ALERTS_SUMMARY', 'Resumo de Alertas', 'OPERATIONAL', 'Alertas por status, severidade e período'),
('CASES_SLA', 'Cases e SLA', 'OPERATIONAL', 'Cases abertos, vencidos e decididos'),
('PEP_EXPOSURE', 'Exposição PEP', 'COMPLIANCE', 'Partes PEP ativas e histórico'),
('SCREENING_PENDING', 'Screening Pendente', 'COMPLIANCE', 'Matches pendentes de revisão'),
('EFFECTIVENESS', 'Relatório de Efetividade', 'REGULATORY', 'Indicadores de efetividade PLD');
