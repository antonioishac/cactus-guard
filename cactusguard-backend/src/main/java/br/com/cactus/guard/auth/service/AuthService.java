package br.com.cactus.guard.auth.service;

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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("User not found"));

        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .toList();

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshTokenValue = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpiration))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

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

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Refresh token expired or revoked");
        }

        User user = refreshToken.getUser();
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .toList();

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken.getToken())
                .expiresIn(1800)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .name(user.getName())
                        .roles(roles)
                        .build())
                .build();
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }
}
