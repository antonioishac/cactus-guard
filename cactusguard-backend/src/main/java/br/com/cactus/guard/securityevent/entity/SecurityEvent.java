package br.com.cactus.guard.securityevent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de seguranca de PLATAFORMA (V44): sem {@code @TenantId} e sem RLS de proposito - tentativas de login
 * ocorrem antes de haver tenant e usernames inexistentes nao pertencem a nenhum. Nunca guarda a senha tentada.
 */
@Entity
@Table(name = "security_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityEvent {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String LOGIN_BLOCKED = "LOGIN_BLOCKED";
    public static final String LOGIN_USER_LOCKED = "LOGIN_USER_LOCKED";
    public static final String LOGIN_IP_LOCKED = "LOGIN_IP_LOCKED";
    public static final String LOGIN_DENIED_TENANT_SUSPENDED = "LOGIN_DENIED_TENANT_SUSPENDED";

    /** Cadeia de hash do audit_log de um tenant nao confere (user_tenant_id = tenant afetado). */
    public static final String AUDIT_CHAIN_BROKEN = "AUDIT_CHAIN_BROKEN";

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILURE = "FAILURE";
    public static final String RESULT_BLOCKED = "BLOCKED";
    public static final String RESULT_DENIED = "DENIED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(name = "username_masked", length = 100)
    private String usernameMasked;

    /** HMAC-SHA256 do username normalizado; chave dos contadores de bloqueio. */
    @Column(name = "username_hash", length = 64)
    private String usernameHash;

    /** Informativo: preenchido apenas quando o usuario existe. Sem FK. */
    @Column(name = "user_tenant_id")
    private UUID tenantId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 200)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
