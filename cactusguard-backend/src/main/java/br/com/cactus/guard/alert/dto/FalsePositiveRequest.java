package br.com.cactus.guard.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FalsePositiveRequest(
        @NotBlank @Size(max = 100) String reason,
        @NotBlank String justification) {
}
