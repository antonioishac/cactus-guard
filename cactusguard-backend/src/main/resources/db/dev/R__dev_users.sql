-- =====================================================================
-- Usuários de DESENVOLVIMENTO — lido pelo Flyway SOMENTE no perfil "dev"
-- (spring.flyway.locations em application-dev.yml). Produção nunca vê este arquivo.
--
--   usuário      senha      perfil       para que serve
--   admin        admin123   ADMIN        usuários, configuração, migração
--   analista     dev12345   ANALYST      analisa alertas e decide cases
--   supervisor   dev12345   SUPERVISOR   aprova (quatro olhos), SISCOAF, regras
--   supervisor2  dev12345   SUPERVISOR   segundo aprovador (testar quatro olhos)
--   auditor      dev12345   AUDITOR      somente leitura + trilha de auditoria
--
-- Senhas em BCrypt (gerado com spring-security-crypto). Para trocar uma senha,
-- gere um novo hash e edite aqui: o Flyway reaplica este arquivo quando ele muda.
-- Idempotente: não duplica nem sobrescreve usuários existentes.
-- =====================================================================

INSERT INTO users (username, email, password, name, active) VALUES
  ('admin',       'admin@cactusguard.dev',       '$2a$10$tDiz5O/hpBGShV9Xs050seN1ja5bQ7rCybrp6jBHqc23qJ9B//LYK', 'Administrador',     true),
  ('analista',    'analista@cactusguard.dev',    '$2a$10$0LsAOBlZk52B4/ZDgD7U1eMAqpISRFRfFK1.5r0v5gluXOeXsBngu', 'Ana Analista',      true),
  ('supervisor',  'supervisor@cactusguard.dev',  '$2a$10$0LsAOBlZk52B4/ZDgD7U1eMAqpISRFRfFK1.5r0v5gluXOeXsBngu', 'Sérgio Supervisor', true),
  ('supervisor2', 'supervisor2@cactusguard.dev', '$2a$10$0LsAOBlZk52B4/ZDgD7U1eMAqpISRFRfFK1.5r0v5gluXOeXsBngu', 'Sofia Supervisora', true),
  ('auditor',     'auditor@cactusguard.dev',     '$2a$10$0LsAOBlZk52B4/ZDgD7U1eMAqpISRFRfFK1.5r0v5gluXOeXsBngu', 'Augusto Auditor',   true)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM (VALUES ('admin', 'ADMIN'),
             ('analista', 'ANALYST'),
             ('supervisor', 'SUPERVISOR'),
             ('supervisor2', 'SUPERVISOR'),
             ('auditor', 'AUDITOR')) AS seed(username, role_name)
JOIN users u ON u.username = seed.username
JOIN roles r ON r.name = seed.role_name
ON CONFLICT DO NOTHING;
