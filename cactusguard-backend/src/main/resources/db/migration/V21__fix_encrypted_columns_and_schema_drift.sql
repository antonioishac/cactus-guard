-- V21: Correcoes de colunas criptografadas, full-text e divergencias entity x schema
--
-- Contexto: Party.document/email/phone sao gravados via EncryptedStringConverter
-- (AES-256-GCM, IV aleatorio, base64). O texto cifrado e bem maior que o claro e
-- muda a cada gravacao, portanto:
--   * VARCHAR(20) em document estoura no insert;
--   * UNIQUE em document nao impede duplicidade (mesmo CPF gera ciphertexts diferentes);
--   * indices btree/trigram e tsvector sobre ciphertext sao inuteis.
-- A unicidade passa a ser garantida por document_hash (SHA-256 do documento normalizado).

-- 1) parties.document ------------------------------------------------------------
DROP INDEX IF EXISTS idx_parties_document_trgm;
DROP INDEX IF EXISTS idx_parties_document;
ALTER TABLE parties DROP CONSTRAINT IF EXISTS parties_document_key;
ALTER TABLE parties ALTER COLUMN document TYPE VARCHAR(512);

-- 2) email / phone (tambem criptografados) -----------------------------------------
ALTER TABLE parties ALTER COLUMN email TYPE VARCHAR(512);
ALTER TABLE parties ALTER COLUMN phone TYPE VARCHAR(512);

-- 3) Unicidade por hash do documento -----------------------------------------------
-- Falha propositalmente se houver hashes duplicados: duplicidade de cadastro deve ser
-- saneada antes (cliente duplicado distorce perfil, risco e monitoramento).
-- Registros anteriores a V17 podem ter document_hash NULL: a aplicacao deve fazer o
-- backfill (decifrar -> normalizar -> SHA-256). NULLs nao conflitam no indice.
DROP INDEX IF EXISTS idx_parties_document_hash;
CREATE UNIQUE INDEX IF NOT EXISTS uq_parties_document_hash ON parties(document_hash);

-- 4) Full-text: indexar apenas campos em claro (name, trade_name) -------------------
CREATE OR REPLACE FUNCTION parties_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('portuguese', coalesce(NEW.name, '')), 'A') ||
        setweight(to_tsvector('portuguese', coalesce(NEW.trade_name, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS parties_search_vector_trigger ON parties;
CREATE TRIGGER parties_search_vector_trigger
    BEFORE INSERT OR UPDATE OF name, trade_name ON parties
    FOR EACH ROW EXECUTE FUNCTION parties_search_vector_update();

-- Recalcula o vetor de todos os registros (remove tokens de ciphertext ja indexados).
UPDATE parties SET search_vector =
    setweight(to_tsvector('portuguese', coalesce(name, '')), 'A') ||
    setweight(to_tsvector('portuguese', coalesce(trade_name, '')), 'B');

-- 5) beneficial_owners.owner_document ----------------------------------------------
-- Hoje em claro na entity; alargado para permitir criptografia futura sem nova
-- migration (LGPD: CPF de socio/UBO e dado pessoal). Ver owner_document_hash em V23.
ALTER TABLE beneficial_owners ALTER COLUMN owner_document TYPE VARCHAR(512);

-- 6) kyc_documents: colunas usadas pela entity KycDocument e ausentes em V10 --------
ALTER TABLE kyc_documents ADD COLUMN IF NOT EXISTS content BYTEA;
ALTER TABLE kyc_documents ADD COLUMN IF NOT EXISTS content_type VARCHAR(100);
-- Integridade da evidencia documental (prova de que o arquivo nao foi alterado).
ALTER TABLE kyc_documents ADD COLUMN IF NOT EXISTS sha256 VARCHAR(64);
ALTER TABLE kyc_documents ADD COLUMN IF NOT EXISTS size_bytes BIGINT;

-- 7) kyc_processes.status: KycOrchestrator grava DOCUMENT_VALIDATED -----------------
ALTER TABLE kyc_processes DROP CONSTRAINT IF EXISTS kyc_processes_status_check;
ALTER TABLE kyc_processes ADD CONSTRAINT kyc_processes_status_check
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DOCUMENT_VALIDATED', 'APPROVED', 'REJECTED', 'EXPIRED'));

ALTER TABLE kyc_processes ADD CONSTRAINT chk_kyc_processes_risk_level
    CHECK (risk_level IS NULL OR risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL', 'PENDING'));
