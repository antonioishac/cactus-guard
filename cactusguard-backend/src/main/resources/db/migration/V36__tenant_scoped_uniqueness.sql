-- V36: multi-tenancy - passo 4: unicidades passam a ser POR TENANT.
-- Revisao de todos os indices unicos do schema:
--   por tenant (aqui) : parties.document_hash, monitoring_rules.code, cases.case_number, case_number_counters,
--                       alerts.dedup_key (alertas abertos), siscoaf COE por transacao, declaracao negativa por ano,
--                       users.email, user_devices.token
--   permanecem globais: users.username (decisao: login nao pede tenant, entao o username identifica o tenant;
--                       ver spec 047), roles.name, screening_lists.code, report_definitions.code, refresh_tokens.token_hash,
--                       uq_hrj_current (global)
--   ja implicitamente por tenant (chave e UUID unico): transaction_profiles.party_id, uq_alert_transactions,
--                       uq_rule_versions, uq_party_rel_current, user_push_preferences.user_id

DROP INDEX uq_parties_document_hash;
CREATE UNIQUE INDEX uq_parties_tenant_document_hash ON parties (tenant_id, document_hash);

ALTER TABLE monitoring_rules DROP CONSTRAINT monitoring_rules_code_key;
ALTER TABLE monitoring_rules ADD CONSTRAINT uq_monitoring_rules_tenant_code UNIQUE (tenant_id, code);

DROP INDEX uq_cases_case_number;
CREATE UNIQUE INDEX uq_cases_tenant_case_number ON cases (tenant_id, case_number) WHERE case_number IS NOT NULL;

ALTER TABLE case_number_counters DROP CONSTRAINT case_number_counters_pkey;
ALTER TABLE case_number_counters ADD CONSTRAINT case_number_counters_pkey PRIMARY KEY (tenant_id, counter_year);

DROP INDEX uq_alerts_dedup_open;
CREATE UNIQUE INDEX uq_alerts_tenant_dedup_open ON alerts (tenant_id, dedup_key)
    WHERE dedup_key IS NOT NULL AND status IN ('OPEN', 'IN_ANALYSIS', 'ESCALATED');

DROP INDEX uq_siscoaf_coe_transaction;
CREATE UNIQUE INDEX uq_siscoaf_tenant_coe_transaction ON siscoaf_communications (tenant_id, transaction_id)
    WHERE type = 'COE' AND transaction_id IS NOT NULL AND status NOT IN ('CANCELLED', 'RECTIFIED', 'REJECTED');

DROP INDEX uq_siscoaf_negative_year;
CREATE UNIQUE INDEX uq_siscoaf_tenant_negative_year ON siscoaf_communications (tenant_id, reference_year)
    WHERE type = 'NEGATIVE_DECLARATION' AND status NOT IN ('CANCELLED', 'RECTIFIED', 'REJECTED');

ALTER TABLE users DROP CONSTRAINT users_email_key;
ALTER TABLE users ADD CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email);

ALTER TABLE user_devices DROP CONSTRAINT uq_user_devices_token;
ALTER TABLE user_devices ADD CONSTRAINT uq_user_devices_tenant_token UNIQUE (tenant_id, token);
