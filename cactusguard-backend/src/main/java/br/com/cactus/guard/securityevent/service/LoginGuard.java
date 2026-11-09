package br.com.cactus.guard.securityevent.service;

import br.com.cactus.guard.securityevent.dto.ClientInfo;
import br.com.cactus.guard.securityevent.entity.SecurityEvent;
import br.com.cactus.guard.securityevent.repository.SecurityEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Registro de tentativas de login e bloqueio progressivo (nunca grava a senha tentada).
 * <ul>
 *   <li>Por usuario: {@code max-failures-per-user} falhas na janela ({@code window-minutes}) desde o ultimo login bem-sucedido
 *       geram um evento LOGIN_USER_LOCKED; enquanto ele estiver dentro de {@code lockout-minutes}, o login e recusado
 *       (mesmo com a senha certa). Login bem-sucedido zera o contador (so falhas posteriores a ele contam).</li>
 *   <li>Por IP: {@code max-failures-per-ip} falhas na janela geram LOGIN_IP_LOCKED. Sucesso NAO zera o contador do IP
 *       (o atacante poderia alternar com uma conta propria valida).</li>
 *   <li>Usernames inexistentes passam pelo MESMO caminho (hash do username), portanto o bloqueio e a resposta nao revelam
 *       se o usuario existe.</li>
 * </ul>
 * Os eventos sao gravados em transacao propria (persistem mesmo quando o login falha com excecao).
 */
@Slf4j
@Service
public class LoginGuard {

    private final SecurityEventRepository repository;
    private final byte[] hmacKey;
    private final int maxFailuresPerUser;
    private final int maxFailuresPerIp;
    private final Duration window;
    private final Duration lockout;

    public LoginGuard(SecurityEventRepository repository,
                      @Value("${security.document-hash.key:}") String key,
                      @Value("${security.login-protection.max-failures-per-user:5}") int maxFailuresPerUser,
                      @Value("${security.login-protection.max-failures-per-ip:20}") int maxFailuresPerIp,
                      @Value("${security.login-protection.window-minutes:15}") long windowMinutes,
                      @Value("${security.login-protection.lockout-minutes:15}") long lockoutMinutes) {
        this.repository = repository;
        this.hmacKey = key.getBytes(StandardCharsets.UTF_8);
        this.maxFailuresPerUser = maxFailuresPerUser;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.window = Duration.ofMinutes(windowMinutes);
        this.lockout = Duration.ofMinutes(lockoutMinutes);
    }

    /** True se o usuario OU o IP estao em bloqueio temporario. */
    public boolean isBlocked(String username, ClientInfo client) {
        Instant now = Instant.now();
        if (repository.existsByUsernameHashAndEventTypeAndOccurredAtAfter(
                usernameHash(username), SecurityEvent.LOGIN_USER_LOCKED, now.minus(lockout))) {
            return true;
        }
        String ip = client.ip();
        return ip != null && repository.existsByIpAddressAndEventTypeAndOccurredAtAfter(
                ip, SecurityEvent.LOGIN_IP_LOCKED, now.minus(lockout));
    }

    public void recordBlocked(String username, ClientInfo client) {
        save(SecurityEvent.LOGIN_BLOCKED, SecurityEvent.RESULT_BLOCKED, username, null, client);
        log.warn("Login recusado por bloqueio temporario");
    }

    /** Registra a falha e, se o limite foi atingido, o evento de bloqueio. */
    public void recordFailure(String username, UUID tenantId, ClientInfo client) {
        save(SecurityEvent.LOGIN_FAILURE, SecurityEvent.RESULT_FAILURE, username, tenantId, client);
        Instant now = Instant.now();
        Instant windowStart = now.minus(window);

        String hash = usernameHash(username);
        Instant userSince = repository.findFirstByUsernameHashAndEventTypeOrderByOccurredAtDesc(hash, SecurityEvent.LOGIN_SUCCESS)
                .map(SecurityEvent::getOccurredAt)
                .filter(windowStart::isBefore)
                .orElse(windowStart);
        if (repository.countByUsernameHashAndEventTypeAndOccurredAtAfter(hash, SecurityEvent.LOGIN_FAILURE, userSince)
                >= maxFailuresPerUser) {
            save(SecurityEvent.LOGIN_USER_LOCKED, SecurityEvent.RESULT_BLOCKED, username, tenantId, client);
            log.warn("Usuario bloqueado temporariamente apos {} falhas de login", maxFailuresPerUser);
        }

        String ip = client.ip();
        if (ip != null && repository.countByIpAddressAndEventTypeAndOccurredAtAfter(
                ip, SecurityEvent.LOGIN_FAILURE, windowStart) >= maxFailuresPerIp) {
            save(SecurityEvent.LOGIN_IP_LOCKED, SecurityEvent.RESULT_BLOCKED, username, tenantId, client);
            log.warn("IP bloqueado temporariamente apos {} falhas de login", maxFailuresPerIp);
        }
    }

    public void recordSuccess(String username, UUID tenantId, ClientInfo client) {
        save(SecurityEvent.LOGIN_SUCCESS, SecurityEvent.RESULT_SUCCESS, username, tenantId, client);
    }

    public void recordTenantSuspended(String username, UUID tenantId, ClientInfo client) {
        save(SecurityEvent.LOGIN_DENIED_TENANT_SUSPENDED, SecurityEvent.RESULT_DENIED, username, tenantId, client);
    }

    private void save(String type, String result, String username, UUID tenantId, ClientInfo client) {
        repository.save(SecurityEvent.builder()
                .eventType(type)
                .result(result)
                .usernameMasked(mask(username))
                .usernameHash(usernameHash(username))
                .tenantId(tenantId)
                .ipAddress(client.ip())
                .userAgent(client.userAgent())
                .build());
    }

    /** HMAC-SHA256 (hex) do username normalizado; separado por prefixo de qualquer outro uso da chave. */
    public String usernameHash(String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            String normalized = "login-username:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponivel", e);
        }
    }

    static String mask(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        String u = username.trim().replaceAll("\\p{Cntrl}", "");
        if (u.length() <= 2) {
            return "**";
        }
        String masked = u.charAt(0) + "***" + u.charAt(u.length() - 1);
        return masked.length() > 100 ? masked.substring(0, 100) : masked;
    }
}
