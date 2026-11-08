package br.com.cactus.guard.device.controller;

import br.com.cactus.guard.device.dto.DeviceRegisterRequest;
import br.com.cactus.guard.device.dto.DeviceResponse;
import br.com.cactus.guard.device.dto.PushPreferencesDto;
import br.com.cactus.guard.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Dispositivos e Push")
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private static final String ALL_ROLES = "hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')";

    private final DeviceService service;

    @PostMapping
    @PreAuthorize(ALL_ROLES)
    @Operation(summary = "Registra/atualiza o token de push do aparelho (upsert por token; vincula ao usuario autenticado)")
    public ResponseEntity<DeviceResponse> register(@Valid @RequestBody DeviceRegisterRequest request,
                                                   Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(auth.getName(), request).device());
    }

    @DeleteMapping("/{token}")
    @PreAuthorize(ALL_ROLES)
    @Operation(summary = "Revoga o token do aparelho (somente o dono)")
    public ResponseEntity<Void> revoke(@PathVariable String token, Authentication auth) {
        service.revoke(auth.getName(), token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    @PreAuthorize(ALL_ROLES)
    @Operation(summary = "Preferencias de push do usuario autenticado")
    public ResponseEntity<PushPreferencesDto> getPreferences(Authentication auth) {
        return ResponseEntity.ok(service.getPreferences(auth.getName()));
    }

    @PutMapping("/preferences")
    @PreAuthorize(ALL_ROLES)
    @Operation(summary = "Atualiza preferencias de push (alertas criticos / prazos)")
    public ResponseEntity<PushPreferencesDto> updatePreferences(@Valid @RequestBody PushPreferencesDto dto,
                                                                Authentication auth) {
        return ResponseEntity.ok(service.updatePreferences(auth.getName(), dto));
    }
}
