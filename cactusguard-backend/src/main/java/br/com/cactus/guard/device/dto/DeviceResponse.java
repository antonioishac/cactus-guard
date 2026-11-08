package br.com.cactus.guard.device.dto;

import br.com.cactus.guard.device.entity.DevicePlatform;

import java.time.Instant;
import java.util.UUID;

/** Nunca devolve o token em claro. */
public record DeviceResponse(UUID id, DevicePlatform platform, String appVersion, Instant createdAt, Instant lastSeenAt) {
}
