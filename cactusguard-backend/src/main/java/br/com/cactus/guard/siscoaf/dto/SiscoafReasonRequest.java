package br.com.cactus.guard.siscoaf.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record SiscoafReasonRequest(@NotBlank String reason, Map<String, Object> payload) {
}
