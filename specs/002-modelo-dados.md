# 002 — Modelo de Dados Completo (Revisado - Sem Lacunas)

> Visão lógica resumida. O detalhamento de **cada campo, por que importa para PLD/fraude e a norma que o exige** está em `046-catalogo-campos-pld.md`. Schema físico = migrations Flyway.

## Princípios
- Multi-tenant: `tenant_id` + filtro Hibernate (047) + RLS como defesa em profundidade. **Pendente no código.**
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
document_hash, mother_name, birth_date, nationality, rf_status (incl. titular falecido / PJ inapta), rf_checked_at
PJ: incorporation_date, cnae_primary/secondary, legal_nature, share_capital, ubo_identified
blocked_csnu, blocked_at (Lei 13.810)

### PartyKYC / Profile (Versionado)
id, party_id, version, occupation/cnae, declared_income/revenue, declared_net_worth/equity, source_of_funds, source_of_wealth, relationship_purpose, address (JSONB), contact, valid_from, valid_to

### BeneficialOwner
id, party_id (empresa), owner_party_id, ownership_pct_direct, ownership_pct_indirect, ownership_pct_total, control_type, ownership_chain (JSONB), is_final, valid_from, valid_to, calculated_at

### PEPRelationship
id, party_id, related_party_id, relationship_type (family_2nd, close_collaborator, representative), details (JSONB), valid_from, valid_to

### PEPPosition
id, party_id, role, organ, jurisdiction, role_start_date, role_end_date, pep_expiry_date (= fim + 5 anos), source, senior_approval_by, senior_approval_at, senior_approval_justification

### RiskClassification (Versionado)
id, party_id, score, category, factors (JSONB), methodology_version, calculated_at, valid_until, triggered_by

### ScreeningResult
id, party_id, list_type (PEP, OFAC, ONU, UE, adverse_media, internal), hit_status, match_score, details (JSONB), screened_at, next_due, reviewed_by

### Transaction
id, tenant_id, party_id, counterparty_party_id, amount, currency, type (incl. cash_withdrawal_provision), channel, payment_method, direction, occurred_at, external_reference, is_cash, metadata (JSONB), source_batch_id
pix_end_to_end_id, own_branch, own_account, counterparty_document_hash, counterparty_name, counterparty_ispb, counterparty_branch, counterparty_account, counterparty_country

### TransactionAggregate
party_id, period, total_volume, count, avg_ticket, cash_percentage, unique_counterparties, calculated_at

### MonitoringRule (Versionado)
id, tenant_id, code, name, scenario_type, cc4001_indicator, condition (JSONB), rationale, severity, is_active, version, created_by, approved_by, activated_at, active_to, backtest_result (JSONB)

### Alert
id, tenant_id, party_id, alert_type (transactional, pep, screening, sla, data_quality, system), rule_id, rule_version, cc4001_indicator, severity, status, occurred_at, selection_deadline (+45d), triggered_at, assigned_to, due_date, related_ids (JSONB), evidence_snapshot (JSONB), description

### Case
id, alert_id, party_id, status, decision (COMMUNICATE | ARCHIVE), justification, analyst_id, decided_at, requires_approval, approved_by, approved_at, approval_justification, selected_at, analysis_deadline (+45d), communication_deadline (D+1 útil), opened_at, closed_at, sla_deadline, is_edd, cc4001_indicators (JSONB), retention_until

### CaseEvidence
id, case_id, file_name, storage_ref, sha256, uploaded_by, uploaded_at

### Communication
id, case_id, type (COS, COE, automatic_cash, csnu_freeze, negative_declaration), status, deadline, sent_at, coaf_protocol, payload (criptografado), payload_hash, reference_year, attempts, last_error

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