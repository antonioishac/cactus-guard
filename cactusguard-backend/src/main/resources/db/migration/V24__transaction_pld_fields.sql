-- V24: Campos PLD/FT de transactions
--
-- PARTICIONAMENTO (recomendado, NAO aplicado aqui): transactions e a tabela de maior
-- volume e as consultas do motor de regras sao sempre por janela de data. Recomenda-se
-- convertê-la para PARTITION BY RANGE (transaction_date) com particoes mensais (ex.:
-- pg_partman), o que exige PK composta (id, transaction_date) e recriacao das FKs que
-- apontam para transactions (alert_transactions, siscoaf_communications). A conversao
-- deve ser feita em migration dedicada com janela de manutencao.

-- Direcao e meio de pagamento
ALTER TABLE transactions ADD COLUMN direction VARCHAR(3);
ALTER TABLE transactions ADD CONSTRAINT chk_transactions_direction CHECK (direction IS NULL OR direction IN ('IN', 'OUT'));
ALTER TABLE transactions ADD COLUMN is_cash BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE transactions ADD COLUMN payment_method VARCHAR(20);
ALTER TABLE transactions ADD CONSTRAINT chk_transactions_payment_method CHECK (payment_method IS NULL OR payment_method IN
    ('PIX', 'TED', 'DOC', 'BOLETO', 'CASH', 'CARD', 'FX', 'CRYPTO', 'INTERNAL'));

-- Rastreabilidade
ALTER TABLE transactions ADD COLUMN external_reference VARCHAR(100);
ALTER TABLE transactions ADD COLUMN end_to_end_id VARCHAR(64);        -- PIX E2E (32 chars)
ALTER TABLE transactions ADD COLUMN account_number VARCHAR(30);
ALTER TABLE transactions ADD COLUMN source_batch_id UUID;            -- lote de ingestao/migracao
ALTER TABLE transactions ADD COLUMN metadata JSONB;

-- Contraparte
ALTER TABLE transactions ADD COLUMN counterparty_party_id UUID REFERENCES parties(id);
ALTER TABLE transactions ADD COLUMN counterparty_bank_ispb VARCHAR(8);
ALTER TABLE transactions ADD COLUMN counterparty_branch VARCHAR(10);
ALTER TABLE transactions ADD COLUMN counterparty_account VARCHAR(30);
ALTER TABLE transactions ADD COLUMN counterparty_country VARCHAR(3);
-- Adicional: hash do documento da contraparte - permite agregar contrapartes
-- recorrentes e cruzar com watchlist/listas sem depender do documento em claro.
ALTER TABLE transactions ADD COLUMN counterparty_document_hash VARCHAR(64);

-- Cambio
ALTER TABLE transactions ADD COLUMN amount_brl NUMERIC(18,2);
ALTER TABLE transactions ADD COLUMN fx_rate NUMERIC(18,8);

-- Especie: provisionamento de saque (Circular 3.978 - saques em especie >= R$ 50 mil
-- exigem provisionamento previo e registro). Adicional: data agendada do saque.
ALTER TABLE transactions ADD COLUMN cash_withdrawal_provision BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE transactions ADD COLUMN provision_scheduled_date DATE;

-- Adicional: estorno/devolucao (MED do PIX, chargeback) - estornos recorrentes sao
-- indicio de conta de passagem/fraude; vincular ao lancamento original.
ALTER TABLE transactions ADD COLUMN reversal_of_id UUID REFERENCES transactions(id);

-- Indices
CREATE INDEX idx_transactions_party_date ON transactions(party_id, transaction_date);
CREATE INDEX idx_transactions_cash_amount ON transactions(is_cash, amount);
CREATE INDEX idx_transactions_counterparty_party ON transactions(counterparty_party_id);
CREATE INDEX idx_transactions_counterparty_doc_hash ON transactions(counterparty_document_hash);
CREATE INDEX idx_transactions_e2e ON transactions(end_to_end_id);
CREATE INDEX idx_transactions_external_ref ON transactions(external_reference);
CREATE INDEX idx_transactions_payment_method ON transactions(payment_method);
CREATE INDEX idx_transactions_source_batch ON transactions(source_batch_id);
CREATE INDEX idx_transactions_cp_country ON transactions(counterparty_country) WHERE counterparty_country IS NOT NULL;
