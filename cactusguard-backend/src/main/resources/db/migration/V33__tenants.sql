-- V33: multi-tenancy (spec 048 B1) - passo 1: cadastro de tenants e role de plataforma.
-- Tenants NAO tem RLS: e catalogo da plataforma (resolvido no login, antes de existir tenant no contexto).
-- IDs fixos: 'default' (...0001) recebe todos os dados existentes; 'platform' (...0002) abriga apenas
-- usuarios PLATFORM_ADMIN (sem dados de negocio).

CREATE TABLE tenants (
    id                  UUID PRIMARY KEY,
    code                VARCHAR(50)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    cnpj                VARCHAR(512),                       -- cifrado (AES-256-GCM), como parties.document
    cnpj_hash           VARCHAR(64),                        -- HMAC-SHA256 com pepper (busca/unicidade)
    regulatory_segment  VARCHAR(30)  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    settings            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_tenants_code UNIQUE (code),
    CONSTRAINT ck_tenants_code CHECK (code ~ '^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$'),
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_tenants_segment CHECK (regulatory_segment IN
        ('BANK', 'PAYMENT_INSTITUTION', 'SCD', 'SEP', 'BROKER_CVM', 'INSURER_SUSEP', 'VASP', 'OTHER'))
);
CREATE UNIQUE INDEX uq_tenants_cnpj_hash ON tenants(cnpj_hash) WHERE cnpj_hash IS NOT NULL;

INSERT INTO tenants (id, code, name, regulatory_segment, status) VALUES
 ('00000000-0000-0000-0000-000000000001', 'default',  'Tenant padrão (dados pré multi-tenancy)', 'OTHER', 'ACTIVE'),
 ('00000000-0000-0000-0000-000000000002', 'platform', 'Plataforma CactusGuard',                  'OTHER', 'ACTIVE');

INSERT INTO roles (name, description)
VALUES ('PLATFORM_ADMIN', 'Administrador da plataforma (gestão de tenants; sem acesso a dados de negócio)')
ON CONFLICT (name) DO NOTHING;
