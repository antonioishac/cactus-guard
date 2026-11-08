package br.com.cactus.guard.siscoaf.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code status} = status resultante da transicao. */
public record SiscoafHistoryEntry(String action, String status, String fromStatus, UUID byId, String byName,
                                  String reason, Instant at) {
}
