-- V30: PII remanescente em claro + suporte aos filtros server-side e ao motivo de revelacao de PII.
--
-- 1) transactions.counterparty_document passa a ser cifrado (AES-256-GCM, EncryptedStringConverter):
--    o ciphertext (prefixo de versao + base64) nao cabe em VARCHAR(20).
--    beneficial_owners.owner_document ja e VARCHAR(512) desde a V21.
--    Dados legados em claro sao cifrados pela aplicacao (DocumentRehashService.encryptLegacyPlaintext,
--    executado na inicializacao e em POST /api/v1/admin/rehash-documents): a chave nao existe no banco.
ALTER TABLE transactions ALTER COLUMN counterparty_document TYPE VARCHAR(512);

-- 2) Motivo da revelacao de PII (POST /parties/{id}/reveal) pode ter ate 500 caracteres.
--    Alargar VARCHAR nao reescreve a tabela nem dispara os triggers append-only (V22).
ALTER TABLE data_access_log ALTER COLUMN access_reason TYPE VARCHAR(500);

-- 3) Indices dos filtros de GET /parties
CREATE INDEX IF NOT EXISTS idx_parties_type ON parties(type);
CREATE INDEX IF NOT EXISTS idx_parties_risk_level ON parties(risk_level);
CREATE INDEX IF NOT EXISTS idx_parties_is_pep ON parties(is_pep);
CREATE INDEX IF NOT EXISTS idx_parties_source_type ON parties(source_type);

-- 4) Indices dos filtros de GET /audit e GET /data-access
CREATE INDEX IF NOT EXISTS idx_audit_log_user_created ON audit_log(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_action_created ON audit_log(action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity_type_created ON audit_log(entity_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_data_access_created ON data_access_log(created_at DESC);
