package br.com.cactus.guard.case_.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CaseNoteRequest(@NotBlank @Size(max = 5000) String text) {
}
