package br.com.cactus.guard.audit.service;

import br.com.cactus.guard.audit.dto.AuditIntegrityResponse;
import br.com.cactus.guard.audit.dto.AuditIntegrityResponse.Anchor;
import br.com.cactus.guard.audit.dto.AuditIntegrityResponse.Divergence;
import br.com.cactus.guard.event.dto.DomainEventType;
import br.com.cactus.guard.event.service.DomainEventPublisher;
import br.com.cactus.guard.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verificacao e ancoragem da cadeia de hash do audit_log (V45). O hash e SEMPRE calculado no banco
 * ({@code audit_row_hash}, a mesma funcao do trigger de insercao); aqui so se recalcula e se compara.
 * Registros anteriores a V45 (chain_seq nulo) nao fazem parte da cadeia.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditIntegrityService {

    private static final String FIRST_BAD_LINK = """
            SELECT id, chain_seq, created_at,
                   CASE WHEN hash IS DISTINCT FROM calc THEN 'HASH_MISMATCH'
                        WHEN prev_seq IS NOT NULL AND chain_seq <> prev_seq + 1 THEN 'SEQUENCE_GAP'
                        ELSE 'BROKEN_LINK' END AS reason
              FROM (SELECT a.id, a.chain_seq, a.created_at, a.hash, a.prev_hash,
                           audit_row_hash(a.prev_hash, a) AS calc,
                           lag(a.hash) OVER w AS exp_prev, lag(a.chain_seq) OVER w AS prev_seq
                      FROM audit_log a
                     WHERE a.tenant_id = ? AND a.chain_seq IS NOT NULL
                    WINDOW w AS (ORDER BY a.chain_seq)) x
             WHERE hash IS DISTINCT FROM calc
                OR (prev_seq IS NOT NULL AND (chain_seq <> prev_seq + 1 OR prev_hash IS DISTINCT FROM exp_prev))
                OR (chain_seq = 1 AND prev_hash IS DISTINCT FROM audit_genesis_hash(?))
             ORDER BY chain_seq
             LIMIT 1
            """;

    private final JdbcTemplate jdbc;
    private final DomainEventPublisher events;

    /** Recalcula a cadeia do tenant corrente. Snapshot unico (REPEATABLE READ): inserts concorrentes nao geram falso positivo. */
    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public AuditIntegrityResponse verify() {
        UUID tenantId = TenantContext.require();
        Divergence divergence = jdbc.query(FIRST_BAD_LINK, rs -> rs.next()
                ? new Divergence(rs.getObject("id", UUID.class), rs.getLong("chain_seq"), rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant()) : null, tenantId, tenantId);

        long[] stats = jdbc.query("SELECT count(*) AS n, coalesce(max(chain_seq), 0) AS max_seq FROM audit_log "
                        + "WHERE tenant_id = ? AND chain_seq IS NOT NULL",
                rs -> {
                    rs.next();
                    return new long[]{rs.getLong("n"), rs.getLong("max_seq")};
                }, tenantId);
        String lastHash = jdbc.query("SELECT hash FROM audit_log WHERE tenant_id = ? AND chain_seq = ?",
                rs -> rs.next() ? rs.getString(1) : null, tenantId, stats[1]);

        Long headSeq = null;
        String headHash = null;
        List<Map<String, Object>> head = jdbc.queryForList(
                "SELECT head_seq, head_hash FROM audit_chain_heads WHERE tenant_id = ?", tenantId);
        if (!head.isEmpty()) {
            headSeq = ((Number) head.get(0).get("head_seq")).longValue();
            headHash = (String) head.get(0).get("head_hash");
        }

        if (divergence == null) {
            // cauda: linhas apagadas/acrescentadas fora do trigger deixam a ultima linha diferente da cabeca
            boolean headOk = headSeq == null ? stats[0] == 0
                    : headSeq == stats[1] && (headSeq == 0 || headHash.equals(lastHash));
            if (!headOk) {
                divergence = new Divergence(null, headSeq, "HEAD_MISMATCH", null);
            }
        }
        if (divergence == null) {
            // ancoras dentro do intervalo ainda existente (linhas expurgadas pela retencao ficam de fora)
            divergence = jdbc.query("""
                    SELECT a.head_seq, l.id FROM audit_chain_anchors a
                      LEFT JOIN audit_log l ON l.tenant_id = a.tenant_id AND l.chain_seq = a.head_seq
                     WHERE a.tenant_id = ?
                       AND a.head_seq >= (SELECT min(chain_seq) FROM audit_log WHERE tenant_id = ? AND chain_seq IS NOT NULL)
                       AND (l.id IS NULL OR l.hash <> a.head_hash)
                     ORDER BY a.head_seq LIMIT 1
                    """, rs -> rs.next()
                    ? new Divergence(rs.getObject("id", UUID.class), rs.getLong("head_seq"), "ANCHOR_MISMATCH", null) : null,
                    tenantId, tenantId);
        }
        return new AuditIntegrityResponse(divergence == null, stats[0], headSeq, headHash, divergence,
                lastAnchor(tenantId), Instant.now());
    }

    private Anchor lastAnchor(UUID tenantId) {
        return jdbc.query("SELECT head_seq, head_hash, anchored_at FROM audit_chain_anchors "
                        + "WHERE tenant_id = ? ORDER BY head_seq DESC LIMIT 1",
                rs -> rs.next() ? new Anchor(rs.getLong("head_seq"), rs.getString("head_hash"),
                        rs.getTimestamp("anchored_at").toInstant()) : null, tenantId);
    }

    /**
     * Grava a ancora da cabeca atual do tenant corrente (se avancou desde a ultima) e a publica no outbox como
     * {@code cactus.audit.anchor}. Devolve a ancora criada ou {@code null} se nada novo.
     */
    @Transactional
    public Anchor anchor() {
        UUID tenantId = TenantContext.require();
        List<Map<String, Object>> head = jdbc.queryForList(
                "SELECT head_seq, head_hash FROM audit_chain_heads WHERE tenant_id = ?", tenantId);
        if (head.isEmpty() || ((Number) head.get(0).get("head_seq")).longValue() == 0) {
            return null;
        }
        long seq = ((Number) head.get(0).get("head_seq")).longValue();
        String hash = (String) head.get(0).get("head_hash");
        Anchor last = lastAnchor(tenantId);
        if (last != null && last.headSeq() >= seq) {
            return null;
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO audit_chain_anchors (id, tenant_id, head_seq, head_hash, anchored_at) VALUES (?, ?, ?, ?, ?)",
                id, tenantId, seq, hash, Timestamp.from(now));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("headSeq", seq);
        data.put("headHash", hash);
        data.put("anchoredAt", now.toString());
        events.publish(DomainEventType.AUDIT_ANCHOR, id, data);
        log.info("Ancora da cadeia de auditoria gravada: seq={}", seq);
        return new Anchor(seq, hash, now);
    }
}
