package br.com.cactus.guard.party.dto;

/** Filtros de GET /parties. {@code document} e comparado por HMAC (DocumentHash), nunca por LIKE. */
public record PartyFilter(String q, String document, String type, String riskLevel, Boolean pep, String sourceType) {
}
