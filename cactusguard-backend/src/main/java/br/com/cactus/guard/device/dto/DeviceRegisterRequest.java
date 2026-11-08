package br.com.cactus.guard.device.dto;

import br.com.cactus.guard.device.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceRegisterRequest(
        @NotBlank @Size(max = 1024) String token,
        @NotNull DevicePlatform platform,
        @Size(max = 30) String appVersion) {
}
