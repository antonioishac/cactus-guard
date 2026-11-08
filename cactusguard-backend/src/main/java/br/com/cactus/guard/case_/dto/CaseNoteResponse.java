package br.com.cactus.guard.case_.dto;

import java.time.Instant;
import java.util.UUID;

public record CaseNoteResponse(UUID id, UUID authorId, String authorName, String text, Instant createdAt) {
}
