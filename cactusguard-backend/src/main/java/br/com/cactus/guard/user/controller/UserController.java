package br.com.cactus.guard.user.controller;

import br.com.cactus.guard.user.dto.PasswordResetRequest;
import br.com.cactus.guard.user.dto.PasswordResetResponse;
import br.com.cactus.guard.user.dto.UserCreateRequest;
import br.com.cactus.guard.user.dto.UserResponse;
import br.com.cactus.guard.user.dto.UserUpdateRequest;
import br.com.cactus.guard.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Usuarios")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(userService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Edita nome, e-mail e perfis (username imutavel)")
    public ResponseEntity<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.deactivate(id));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reativa usuario desativado")
    public ResponseEntity<UserResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.activate(id));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reset de senha: gera senha temporaria forte (devolvida uma vez) ou define a informada; revoga refresh tokens")
    public ResponseEntity<PasswordResetResponse> resetPassword(@PathVariable UUID id,
            @Valid @RequestBody(required = false) PasswordResetRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(userService.resetPassword(id, request));
    }
}
