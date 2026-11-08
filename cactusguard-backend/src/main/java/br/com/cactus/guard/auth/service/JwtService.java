package br.com.cactus.guard.auth.service;

import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    public static final String TENANT_CLAIM = "tenant_id";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.issuer:cactusguard}")
    private String issuer;

    /** Fail-fast: segredo ausente ou menor que 256 bits impede a inicializacao (nao ha default). */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret (JWT_SECRET) ausente ou menor que 256 bits (32 bytes)");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String username, List<String> roles, UUID tenantId) {
        return Jwts.builder()
                .issuer(issuer)
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim("userId", userId.toString())
                .claim("roles", roles)
                .claim(TENANT_CLAIM, tenantId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        try {
            return extractClaims(token).getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Claim tenant_id do token (vazio se ausente ou malformado). */
    public java.util.Optional<UUID> extractTenantId(String token) {
        try {
            Object v = extractClaims(token).get(TENANT_CLAIM);
            return v == null ? java.util.Optional.empty() : java.util.Optional.of(UUID.fromString(v.toString()));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }
}
