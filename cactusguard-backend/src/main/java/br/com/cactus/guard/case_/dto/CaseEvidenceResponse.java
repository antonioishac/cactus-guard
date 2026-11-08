package br.com.cactus.guard.case_.dto;

import java.time.Instant;
import java.util.UUID;

/** Evidencia do case. O caminho do arquivo no servidor nunca e exposto. */
public record CaseEvidenceResponse(UUID id, String fileName, String contentType, long sizeBytes, String sha256,
                                   String description, UUID uploadedById, String uploadedByName,
                                   Instant uploadedAt) {
}
