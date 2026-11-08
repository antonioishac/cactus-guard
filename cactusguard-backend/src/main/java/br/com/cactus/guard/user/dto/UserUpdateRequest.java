package br.com.cactus.guard.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/** Edicao de usuario (nome, e-mail, perfis). Username e imutavel; senha tem endpoint proprio. */
public record UserUpdateRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotEmpty Set<String> roles) {
}
