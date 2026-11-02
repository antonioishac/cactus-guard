package br.com.cactus.guard.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    @NotBlank
    private String refreshToken;
}
