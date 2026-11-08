package br.com.cactus.guard.alert.dto;

import java.time.Instant;
import java.util.UUID;

public record AlertCommentResponse(UUID id, UUID authorId, String authorName, String text, Instant createdAt) {
}
