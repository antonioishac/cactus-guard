package br.com.cactus.guard.user.dto;

import java.util.UUID;

/**
 * {@code temporaryPassword} so vem preenchida quando GERADA pelo servidor (exibida uma unica vez); quando o
 * ADMIN define a senha, nada e devolvido. O hash nunca e exposto.
 */
public record PasswordResetResponse(UUID userId, String temporaryPassword, boolean refreshTokensRevoked) {
}
