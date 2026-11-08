package br.com.cactus.guard.audit.dto;

import br.com.cactus.guard.audit.entity.AuditLog;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.common.util.PiiMasker;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Registro de auditoria sem a entity: old/new com PII mascarada (por nome de chave). */
public record AuditLogResponse(
        UUID id,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        UserSummary user,
        String ipAddress,
        String hash,
        Instant createdAt) {

    public record UserSummary(UUID id, String username, String name) {
        public static UserSummary of(User u) {
            return u == null ? null : new UserSummary(u.getId(), u.getUsername(), u.getName());
        }
    }

    /** Requer sessao aberta (user LAZY). */
    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                PiiMasker.maskMap(a.getOldValue()), PiiMasker.maskMap(a.getNewValue()),
                UserSummary.of(a.getUser()), a.getIpAddress(), a.getHash(), a.getCreatedAt());
    }
}
