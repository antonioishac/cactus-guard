package br.com.cactus.guard.audit.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Resultado da verificacao da cadeia de hash do audit_log do tenant (V45). Sem conteudo dos registros: so ids,
 * posicao e motivo.
 */
public record AuditIntegrityResponse(
        boolean ok,
        long verifiedRecords,
        Long headSeq,
        String headHash,
        Divergence firstDivergence,
        Anchor lastAnchor,
        Instant checkedAt) {

    /** Motivos: HASH_MISMATCH, BROKEN_LINK, SEQUENCE_GAP, HEAD_MISMATCH, ANCHOR_MISMATCH. */
    public record Divergence(UUID recordId, Long chainSeq, String reason, Instant createdAt) {
    }

    public record Anchor(long headSeq, String headHash, Instant anchoredAt) {
    }
}
