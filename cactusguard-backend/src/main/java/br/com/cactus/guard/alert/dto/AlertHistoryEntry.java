package br.com.cactus.guard.alert.dto;

import java.time.Instant;
import java.util.UUID;

public record AlertHistoryEntry(UUID id, String action, String fromStatus, String toStatus,
                                UUID actorId, String actorName, String justification, Instant createdAt) {
}
