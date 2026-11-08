package br.com.cactus.guard.audit.dto;

import br.com.cactus.guard.audit.entity.DataAccessLog;

import java.time.Instant;
import java.util.UUID;

/** Acesso a dados pessoais (LGPD) sem a entity. */
public record DataAccessLogResponse(
        UUID id,
        AuditLogResponse.UserSummary user,
        String entityType,
        UUID entityId,
        String fieldsAccessed,
        String accessReason,
        String ipAddress,
        Instant createdAt) {

    /** Requer sessao aberta (user LAZY). */
    public static DataAccessLogResponse from(DataAccessLog d) {
        return new DataAccessLogResponse(d.getId(), AuditLogResponse.UserSummary.of(d.getUser()), d.getEntityType(),
                d.getEntityId(), d.getFieldsAccessed(), d.getAccessReason(), d.getIpAddress(), d.getCreatedAt());
    }
}
