-- =====================================================================
-- Usuários de DESENVOLVIMENTO — lido pelo Flyway SOMENTE no perfil "dev"
-- (spring.flyway.locations em application-dev.yml). Produção nunca vê este arquivo.
--
--   usuário      senha      perfil          tenant      para que serve
--   admin        admin123   ADMIN           default     usuários, configuração, migração
--   analista     dev12345   ANALYST         default     analisa alertas e decide cases
--   supervisor   dev12345   SUPERVISOR      default     aprova (quatro olhos), SISCOAF, regras
--   supervisor2  dev12345   SUPERVISOR      default     segundo aprovador (testar quatro olhos)
--   auditor      dev12345   AUDITOR         default     somente leitura + trilha de auditoria
--   analista2    dev12345   ANALYST         demo2       2º tenant: testar isolamento entre clientes
--   platform     dev12345   PLATFORM_ADMIN  platform    gestão de tenants (sem dados de negócio)
--
-- Tenants: 'default' (...0001, dados existentes), 'platform' (...0002, V33) e 'demo2' (...0003, só dev).
-- Senhas em BCrypt (gerado com spring-security-crypto). Para trocar uma senha,
-- gere um novo hash e edite aqui: o Flyway reaplica este arquivo quando ele muda.
-- Idempotente: não duplica nem sobrescreve usuários existentes.
-- Atenção: no dev local app_user é superusuário (ignora RLS); o isolamento valendo aqui é o filtro do Hibernate.
-- =====================================================================

INSERT INTO tenants (id, code, name, regulatory_segment, status) VALUES
  ('00000000-0000-0000-0000-000000000003', 'demo2', 'Instituição Demo 2 (dev)', 'PAYMENT_INSTITUTION', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (tenant_id, username, email, password, name, active) VALUES
  ('00000000-0000-0000-0000-000000000001', 'admin',       'admin@cactusguard.dev',       '$2a$10$tDiz5O/hpBGShV9Xs050seN1ja5bQ7rCybrp6jBHqc23qJ9B//LYK', 'Administrador',     true),
  ('00000000-0000-0000-0000-000000000001', 'analista',    'analista@cactusguard.dev',    '$2a$10$0LsAOBlZk52B4/ZDgD7U1eMAqpISRFRfFK1.5r0v5gluXOeXsBngu', 'Ana Analista',      true),
  ('00000000-0000-0000-0000-000000000001', 'supervisor',  'supervisor@cactusguard.dev',  '$2a$10$0LsAOBlZk52B4/ZDgD7U1eMAqpISRFRfFK1.5r0v5gluXOeXsBngu', 'Sérgio Supervisor', true),
  ('00000000-0000-0000-0000-000000000001', 'supervisor2', 'supervisor2@cactusguard.dev', '$2a$10$0LsAOBlZk52B4/ZDgD7U1eMAqpISRFRfFK1.5r0v5gluXOeXsBngu', 'Sofia Supervisora', true),
  ('00000000-0000-0000-0000-000000000001', 'auditor',     'auditor@cactusguard.dev',     '$2a$10$0LsAOBlZk52B4/ZDgD7U1eMAqpISRFRfFK1.5r0v5gluXOeXsBngu', 'Augusto Auditor',   true),
  ('00000000-0000-0000-0000-000000000003', 'analista2',   'analista2@cactusguard.dev',   '$2a$10$0LsAOBlZk52B4/ZDgD7U1eMAqpISRFRfFK1.5r0v5gluXOeXsBngu', 'Ana Analista (demo2)', true),
  ('00000000-0000-0000-0000-000000000002', 'platform',    'platform@cactusguard.dev',    '$2a$10$0LsAOBlZk52B4/ZDgD7U1eMAqpISRFRfFK1.5r0v5gluXOeXsBngu', 'Operador da Plataforma', true)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM (VALUES ('admin', 'ADMIN'),
             ('analista', 'ANALYST'),
             ('supervisor', 'SUPERVISOR'),
             ('supervisor2', 'SUPERVISOR'),
             ('auditor', 'AUDITOR'),
             ('analista2', 'ANALYST'),
             ('platform', 'PLATFORM_ADMIN')) AS seed(username, role_name)
JOIN users u ON u.username = seed.username
JOIN roles r ON r.name = seed.role_name
ON CONFLICT DO NOTHING;

-- demo2 recebe as regras-modelo (equivalente SQL do provisionamento da plataforma), com a versão 1 de cada regra.
INSERT INTO monitoring_rules
    (tenant_id, code, name, description, category, severity, active, status, condition_expression, threshold_value,
     threshold_period_days, parameters, carta_4001_reference, applies_to_risk_levels, auto_communication, version,
     effective_from)
SELECT '00000000-0000-0000-0000-000000000003', t.code, t.name, t.description, t.category, t.severity, t.active, t.status,
       t.condition_expression, t.threshold_value, t.threshold_period_days, t.parameters, t.carta_4001_reference,
       t.applies_to_risk_levels, t.auto_communication, 1,
       CASE WHEN t.status = 'ACTIVE' THEN NOW() END
FROM monitoring_rule_templates t
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO monitoring_rule_versions (tenant_id, rule_id, version, snapshot, change_reason, effective_from)
SELECT r.tenant_id, r.id, 1, to_jsonb(r), 'Regra-modelo da plataforma (dev demo2)', r.effective_from
FROM monitoring_rules r
WHERE r.tenant_id = '00000000-0000-0000-0000-000000000003'
ON CONFLICT (rule_id, version) DO NOTHING;
