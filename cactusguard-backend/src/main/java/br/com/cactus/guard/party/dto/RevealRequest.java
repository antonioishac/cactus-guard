package br.com.cactus.guard.party.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Revelacao de PII: {@code field} (um) e/ou {@code fields}; {@code reason} obrigatorio (LGPD). */
public record RevealRequest(
        String field,
        List<String> fields,
        @NotBlank @Size(min = 10, max = 500) String reason) {
}
