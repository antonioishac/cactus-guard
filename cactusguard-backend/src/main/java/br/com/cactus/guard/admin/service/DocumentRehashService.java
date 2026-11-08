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
import java.util.regex.Pattern;

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
    private static final Pattern ENCRYPTED = Pattern.compile("^[A-Za-z0-9_-]{1,16}:[A-Za-z0-9+/=]+$");

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

    /** Tabelas/colunas sao constantes internas (nunca entrada do usuario). */
    private long rehashColumn(String table, String docCol, String hashCol) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, " + docCol + " AS doc, " + hashCol + " AS h FROM " + table + " WHERE " + docCol + " IS NOT NULL");
        long updated = 0;
        for (Map<String, Object> row : rows) {
            String doc = String.valueOf(row.get("doc"));
            // coluna pode estar cifrada (prefixo de versao) ou, em dados legados, em claro
            if (ENCRYPTED.matcher(doc).matches() && doc.indexOf(':') > 0 && doc.length() > 24) {
                doc = converter.convertToEntityAttribute(doc);
            }
            String h = DocumentHash.of(doc);
            if (!h.equals(row.get("h"))) {
                jdbc.update("UPDATE " + table + " SET " + hashCol + " = ? WHERE id = ?", h, (UUID) row.get("id"));
                updated++;
            }
        }
        return updated;
    }
}
