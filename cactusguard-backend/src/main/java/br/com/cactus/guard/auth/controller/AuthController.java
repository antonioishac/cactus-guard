package br.com.cactus.guard.auth.controller;

import br.com.cactus.guard.auth.dto.AuthResponse;
import br.com.cactus.guard.auth.dto.LoginRequest;
import br.com.cactus.guard.auth.dto.RefreshTokenRequest;
import br.com.cactus.guard.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              jakarta.servlet.http.HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, br.com.cactus.guard.securityevent.dto.ClientInfo.from(http)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
