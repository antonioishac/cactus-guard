import type { ISODateTime, UUID } from './common'

export interface UserSummary {
  id: UUID
  username: string
  name: string
}

/** AuditLogResponse. old/newValue saem com PII mascarada pelo nome da chave. */
export interface AuditEntry {
  id: UUID
  action: string
  entityType: string | null
  entityId: UUID | null
  oldValue: Record<string, unknown> | null
  newValue: Record<string, unknown> | null
  user: UserSummary | null
  ipAddress: string | null
  /** hash encadeado (integridade da trilha) */
  hash: string | null
  createdAt: ISODateTime
}

/** DataAccessLogResponse (LGPD): quem acessou quais campos de qual entidade e por quê. */
export interface DataAccessEntry {
  id: UUID
  user: UserSummary | null
  entityType: string
  entityId: UUID
  fieldsAccessed: string | null
  accessReason: string | null
  ipAddress: string | null
  createdAt: ISODateTime
}

/** Filtros de GET /audit e GET /data-access (`action` só em /audit). `from`/`to`: AAAA-MM-DD. */
export interface AuditFilters {
  userId?: UUID
  username?: string
  action?: string
  entityType?: string
  entityId?: UUID
  from?: string
  to?: string
}
