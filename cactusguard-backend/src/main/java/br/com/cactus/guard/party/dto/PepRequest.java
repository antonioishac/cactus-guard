package br.com.cactus.guard.party.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/** Marcacao/atualizacao de PEP. related=true indica familiar ou estreito colaborador. */
public record PepRequest(
        @NotBlank @Pattern(regexp = "NATIONAL|FOREIGN|INTERNATIONAL_ORG") String category,
        String role,
        LocalDate startDate,
        LocalDate endDate,
        boolean related,
        @NotBlank String justification) {
}
