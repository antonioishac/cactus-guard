package br.com.cactus.guard.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import br.com.cactus.guard.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import br.com.cactus.guard.auth.dto.AuthResponse;
import br.com.cactus.guard.auth.dto.LoginRequest;
import br.com.cactus.guard.auth.dto.RefreshTokenRequest;
import br.com.cactus.guard.auth.entity.RefreshToken;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.RefreshTokenRepository;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cactus.guard.audit.service.AuditService;
import org.springframework.security.core.AuthenticationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final br.com.cactus.guard.tenant.service.TenantDirectory tenantDirectory;
    private final br.com.cactus.guard.tenant.service.TenantAccessService tenantAccess;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /**
     * Sem @Transactional no metodo: a falha de login deve ser auditada mesmo com a excecao propagada.
     * O tenant e descoberto pelo username (globalmente unico) e todo o restante roda dentro dele.
     */
    public AuthResponse login(LoginRequest request) {
        UUID tenantId = tenantDirectory.findTenantIdByUsername(request.getUsername()).orElse(null);
        if (tenantId == null) {
            // usuario inexistente: mesma excecao de credencial invalida (sem tenant nao ha onde auditar)
            log.warn("Login negado: usuario desconhecido");
            throw new BadCredentialsException("Credenciais invalidas");
        }
        return TenantContext.callAs(tenantId, () -> loginInTenant(request, tenantId));
    }

    private AuthResponse loginInTenant(LoginRequest request, UUID tenantId) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (AuthenticationException e) {
            auditService.log("LOGIN_FAILURE", "USER", null, null, Map.of("username", request.getUsername()));
            throw e;
        }
        // Senha correta, mas tenant suspenso: 403 (so depois de autenticar, para nao revelar o estado a quem nao tem credencial)
        if (!tenantAccess.isActive(tenantId)) {
            auditService.log("LOGIN_DENIED_TENANT_SUSPENDED", "USER", null, null, Map.of("username", request.getUsername()));
            throw new AccessDeniedException("Tenant suspenso");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("User not found"));
        auditService.log("LOGIN_SUCCESS", "USER", user.getId(), null, Map.of("username", user.getUsername()));
        return issueTokens(user);
    }

    /** O refresh chega sem tenant: o tenant e lido da linha do token (refresh_tokens nao tem RLS) e a rotacao roda nele. */
    public AuthResponse refresh(RefreshTokenRequest request) {
        String tokenHash = hash(request.getRefreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));
        UUID tenantId = stored.getTenantId();
        if (!tenantAccess.isActive(tenantId)) {
            throw new AccessDeniedException("Tenant suspenso");
        }
        return TenantContext.callAs(tenantId, () -> tx.execute(status -> rotate(tokenHash)));
    }

    private AuthResponse rotate(String tokenHash) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Refresh token expired or revoked");
        }
        User user = refreshToken.getUser();
        if (!user.isActive()) {
            throw new BusinessException("Refresh token expired or revoked");
        }
        // rotacao: o refresh token usado e invalidado e um novo e emitido
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(refreshTokenValue))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private AuthResponse issueTokens(User user) {
        List<String> roles = user.getRoles().stream().map(r -> r.getName()).toList();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles, user.getTenantId());

        byte[] raw = new byte[48];
        RANDOM.nextBytes(raw);
        String refreshTokenValue = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(hash(refreshTokenValue))
                .tenantId(user.getTenantId())
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpiration))
                .revoked(false)
                .build());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .expiresIn(1800)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .name(user.getName())
                        .roles(roles)
                        .build())
                .build();
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
