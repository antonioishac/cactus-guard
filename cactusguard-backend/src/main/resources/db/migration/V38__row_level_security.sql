-- V38: multi-tenancy - passo 5: Row Level Security (defesa em profundidade sob o filtro Hibernate @TenantId).
--
-- Contrato com a aplicacao: a conexao recebe `app.tenant_id` (GUC de sessao) a cada emprestimo do pool
-- (TenantAwareDataSource). Sem GUC (ou vazio) app_current_tenant() = NULL e a comparacao nunca e verdadeira:
-- ZERO linhas lidas e nenhuma escrita (fail-closed).
--
-- ATENCAO operacional (documentado em specs/047 e no README):
--  * superusuario e papeis com BYPASSRLS ignoram RLS, e FORCE nao os alcanca. O papel de RUNTIME da aplicacao
--    deve ser NOSUPERUSER NOBYPASSRLS e nao-dono das tabelas; migrations rodam com outro papel (dono/BYPASSRLS).
--  * FORCE ROW LEVEL SECURITY: ate o dono das tabelas fica sujeito as policies (se nao tiver BYPASSRLS).
--  * app_find_user_tenant e SECURITY DEFINER: o dono da funcao (papel de migration) precisa de BYPASSRLS.

CREATE OR REPLACE FUNCTION app_current_tenant() RETURNS uuid
    LANGUAGE sql STABLE PARALLEL SAFE
AS $$ SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid $$;

-- Resolve o tenant de um username ANTES de haver tenant no contexto (login). Devolve so o UUID do tenant.
CREATE OR REPLACE FUNCTION app_find_user_tenant(p_username text) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$ SELECT tenant_id FROM users WHERE username = p_username $$;

ALTER TABLE alert_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE alert_history FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON alert_history
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE alert_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE alert_transactions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON alert_transactions
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON alerts
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_log
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE beneficial_owners ENABLE ROW LEVEL SECURITY;
ALTER TABLE beneficial_owners FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON beneficial_owners
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE case_evidences ENABLE ROW LEVEL SECURITY;
ALTER TABLE case_evidences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON case_evidences
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE case_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE case_notes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON case_notes
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE case_number_counters ENABLE ROW LEVEL SECURITY;
ALTER TABLE case_number_counters FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON case_number_counters
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE cases ENABLE ROW LEVEL SECURITY;
ALTER TABLE cases FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON cases
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE data_access_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE data_access_log FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON data_access_log
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE kyc_checklist ENABLE ROW LEVEL SECURITY;
ALTER TABLE kyc_checklist FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON kyc_checklist
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE kyc_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE kyc_documents FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON kyc_documents
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE kyc_processes ENABLE ROW LEVEL SECURITY;
ALTER TABLE kyc_processes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON kyc_processes
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE migration_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE migration_batches FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON migration_batches
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE migration_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE migration_records FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON migration_records
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE monitoring_rule_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE monitoring_rule_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON monitoring_rule_versions
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE monitoring_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE monitoring_rules FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON monitoring_rules
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notifications
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE parties ENABLE ROW LEVEL SECURITY;
ALTER TABLE parties FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON parties
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE party_addresses ENABLE ROW LEVEL SECURITY;
ALTER TABLE party_addresses FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON party_addresses
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE party_relationships ENABLE ROW LEVEL SECURITY;
ALTER TABLE party_relationships FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON party_relationships
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE report_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_jobs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON report_jobs
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE risk_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE risk_history FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON risk_history
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE screening_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE screening_results FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON screening_results
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE siscoaf_communications ENABLE ROW LEVEL SECURITY;
ALTER TABLE siscoaf_communications FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON siscoaf_communications
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE siscoaf_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE siscoaf_history FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON siscoaf_history
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE transaction_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE transaction_profiles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON transaction_profiles
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON transactions
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE user_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_devices FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON user_devices
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE user_push_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_push_preferences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON user_push_preferences
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON users
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

ALTER TABLE watchlist_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE watchlist_entries FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON watchlist_entries
    USING (tenant_id = (SELECT app_current_tenant()))
    WITH CHECK (tenant_id = (SELECT app_current_tenant()));

-- user_roles (tabela de juncao, sem tenant_id): visivel/gravavel apenas para usuarios do tenant corrente.
-- O EXISTS le users sob o RLS de users.
ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_roles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON user_roles
    USING (EXISTS (SELECT 1 FROM users u WHERE u.id = user_roles.user_id))
    WITH CHECK (EXISTS (SELECT 1 FROM users u WHERE u.id = user_roles.user_id));
