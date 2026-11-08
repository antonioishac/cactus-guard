package br.com.cactus.guard.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlertCommentRequest(@NotBlank @Size(max = 5000) String text) {
}
