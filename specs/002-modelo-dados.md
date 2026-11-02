# 002 — Modelo de Dados Completo (Revisado - Sem Lacunas)

## Princípios
- Multi-tenant com RLS.
- Versionamento de KYC, risco, regras e PEPs.
- Suporte a dados migrados (`source_type`: migrated | onboarded).
- Alto volume: particionamento de Transaction e aggregates.
- Reconstrução histórica completa.
- PEP completo (titular + relacionados).
- AuditLog append-only.

## Entidades Principais

### Tenant
id, name, type, regulatory_segment, status, created_at

### Party (Unificado)
id, tenant_id, party_type (customer, ubo, counterparty, employee, partner, supplier), person_type (PF/PJ), document, name, status
current_risk_score, current_risk_category
is_pep, pep_status (active, expired, related), pep_expiry_date
source_type (migrated, onboarded)
onboarding_date / relationship_start, created_at, updated_at

### PartyKYC / Profile (Versionado)
id, party_id, version, occupation/cnae, declared_income/revenue, source_of_funds, purpose, address (JSONB), contact, valid_from, valid_to

### BeneficialOwner
id, party_id (empresa), owner_party_id, ownership_percentage, is_final, calculated_at

### PEPRelationship
id, party_id, related_party_id, relationship_type (family_2nd, close_collaborator, representative), details (JSONB), valid_from, valid_to

### RiskClassification (Versionado)
id, party_id, score, category, factors (JSONB), methodology_version, calculated_at, valid_until, triggered_by

### ScreeningResult
id, party_id, list_type (PEP, OFAC, ONU, UE, adverse_media, internal), hit_status, match_score, details (JSONB), screened_at, next_due, reviewed_by

### Transaction
id, tenant_id, party_id, counterparty_party_id, amount, currency, type, channel, direction, occurred_at, external_reference, is_cash, metadata (JSONB), source_batch_id

### TransactionAggregate
party_id, period, total_volume, count, avg_ticket, cash_percentage, unique_counterparties, calculated_at

### MonitoringRule (Versionado)
id, tenant_id, name, scenario_type, condition (JSONB), severity, is_active, version, created_by, approved_by, activated_at

### Alert
id, tenant_id, party_id, alert_type (transactional, pep, screening, sla, data_quality, system), rule_id, severity, status, triggered_at, assigned_to, due_date, related_ids (JSONB), description

### Case
id, alert_id, party_id, status, decision, justification, analyst_id, opened_at, closed_at, sla_deadline, is_edd

### Communication
id, case_id, type (COS, COE, automatic_cash, negative_declaration), status, sent_at, coaf_protocol, payload

### DataImportBatch
id, tenant_id, source (migration | onboarding_bulk), status, records_total, records_processed, errors (JSONB), started_at, finished_at

### AuditLog
id, tenant_id, entity_type, entity_id, action, user_id, old_value/new_value (JSONB), timestamp

### User (Auth)
id, tenant_id (nullable para usuários multi-tenant), email, password_hash, status, created_at, updated_at

### Role
id, tenant_id, name, description

### Permission
id, code (ex: alert:read, case:decide), description

### UserRole
user_id, role_id

### RolePermission
role_id, permission_id

### RefreshToken
id, user_id, token_hash, expires_at, revoked, created_at

## Observações
- Índices em tenant_id + party_id + occurred_at + source_type.
- Particionamento por data em Transaction.
- JSONB para flexibilidade.
- Soft delete + AuditLog.
- Suporte a reconstrução de PEP status e regras em qualquer data.
- **Full-Text Search**: colunas `tsvector` + índice GIN nas entidades com busca textual (Party.name, Alert.description, etc.). Não usar LIKE como padrão.