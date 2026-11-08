-- V29: role AUDITOR (somente leitura) + refresh token armazenado como hash
--
-- 1) Role AUDITOR: acesso de leitura a trilha de auditoria e dados operacionais, sem poder
--    de escrita (segregacao de funcoes - Circular BCB 3.978/2020).
-- 2) refresh_tokens.token -> token_hash (spec 002). A aplicacao passa a gravar o SHA-256 (hex,
--    64 chars) do refresh token; o valor em claro so existe na resposta do login. Tokens
--    legados (UUID em claro, != 64 chars) sao removidos: os usuarios precisam autenticar novamente.

INSERT INTO roles (name, description)
VALUES ('AUDITOR', 'Auditor (somente leitura)')
ON CONFLICT (name) DO NOTHING;

DELETE FROM refresh_tokens WHERE length(token) <> 64;

ALTER TABLE refresh_tokens RENAME COLUMN token TO token_hash;
ALTER INDEX IF EXISTS idx_refresh_tokens_token RENAME TO idx_refresh_tokens_token_hash;
