package br.com.cactus.guard.admin.service;

import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.common.util.DocumentHash;
import br.com.cactus.guard.crypto.EncryptedStringConverter;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reprocessa os hashes de documento apos a troca de SHA-256 puro por HMAC-SHA256 com pepper
 * (ou apos rotacao do pepper DOCUMENT_HASH_KEY). Decifra {@code parties.document} e recalcula
 * {@code document_hash}; recalcula tambem {@code beneficial_owners.owner_document_hash} e
 * {@code transactions.counterparty_document_hash} a partir do documento de origem.
 * Idempotente. Nunca registra documentos em log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentRehashService {

    private static final int PAGE_SIZE = 200;

    private final PartyRepository partyRepository;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    private final EncryptedStringConverter converter = new EncryptedStringConverter();

    public record Result(long parties, long beneficialOwners, long transactions) { }

    @Transactional
    public Result rehashAll() {
        long parties = 0;
        int page = 0;
        Page<Party> batch;
        do {
            batch = partyRepository.findAll(PageRequest.of(page++, PAGE_SIZE, Sort.by("id")));
            for (Party p : batch) {
                if (p.getDocument() != null) {
                    String h = DocumentHash.of(p.getDocument());
                    if (!h.equals(p.getDocumentHash())) {
                        p.setDocumentHash(h);
                        parties++;
                    }
                }
            }
            partyRepository.saveAll(batch.getContent());
            partyRepository.flush();
        } while (batch.hasNext());

        long owners = rehashColumn("beneficial_owners", "owner_document", "owner_document_hash");
        long txs = rehashColumn("transactions", "counterparty_document", "counterparty_document_hash");

        auditService.log("DOCUMENT_REHASH", "SYSTEM", null, null,
                Map.of("parties", parties, "beneficialOwners", owners, "transactions", txs));
        log.info("Rehash de documentos concluido: parties={}, beneficialOwners={}, transactions={}", parties, owners, txs);
        return new Result(parties, owners, txs);
    }

    /**
     * Cifra documentos de UBO e de contraparte ainda em claro (dados anteriores a V30) e preenche o hash.
     * Idempotente; chamado na inicializacao e por rehashAll.
     */
    @Transactional
    public long encryptLegacyPlaintext() {
        long owners = rehashColumn("beneficial_owners", "owner_document", "owner_document_hash");
        long txs = rehashColumn("transactions", "counterparty_document", "counterparty_document_hash");
        if (owners + txs > 0) {
            auditService.log("PII_LEGACY_ENCRYPTED", "SYSTEM", null, null,
                    Map.of("beneficialOwners", owners, "transactions", txs));
            log.info("PII legada cifrada: beneficialOwners={}, transactions={}", owners, txs);
        }
        return owners + txs;
    }

    /** Tabelas/colunas sao constantes internas (nunca entrada do usuario). */
    private long rehashColumn(String table, String docCol, String hashCol) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, " + docCol + " AS doc, " + hashCol + " AS h FROM " + table + " WHERE " + docCol + " IS NOT NULL");
        long updated = 0;
        for (Map<String, Object> row : rows) {
            String doc = String.valueOf(row.get("doc"));
            UUID id = (UUID) row.get("id");
            // valor cifrado traz prefixo de versao ("v1:..."); sem ":" e dado legado em claro
            boolean legacyPlain = !doc.contains(":");
            String clear = legacyPlain ? doc : converter.convertToEntityAttribute(doc);
            String h = DocumentHash.of(clear);
            if (legacyPlain) {
                jdbc.update("UPDATE " + table + " SET " + docCol + " = ?, " + hashCol + " = ? WHERE id = ?",
                        converter.convertToDatabaseColumn(clear), h, id);
                updated++;
            } else if (!h.equals(row.get("h"))) {
                jdbc.update("UPDATE " + table + " SET " + hashCol + " = ? WHERE id = ?", h, id);
                updated++;
            }
        }
        return updated;
    }
}
