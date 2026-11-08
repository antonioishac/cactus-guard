package br.com.cactus.guard.user.dto;

import jakarta.validation.constraints.Size;

/** {@code newPassword} opcional: ausente = gera senha temporaria forte. */
public record PasswordResetRequest(
        @Size(min = 10, max = 128, message = "deve ter entre 10 e 128 caracteres") String newPassword) {
}
