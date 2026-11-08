import type { ISODateTime, UUID } from './common'

/** Entity AuditLog serializada direto; `user` pode vir aninhado ou ausente. */
export interface AuditEntry {
  id: UUID
  action: string
  entityType: string | null
  entityId: UUID | null
  oldValue?: Record<string, unknown> | null
  newValue?: Record<string, unknown> | null
  user?: { id: UUID; name?: string; username?: string } | null
  createdAt: ISODateTime
  ipAddress?: string | null
  /** hash encadeado (integridade da trilha) */
  hash?: string | null
}

/** Entity DataAccessLog (LGPD): quem acessou quais campos de qual entidade e por quê. */
export interface DataAccessEntry {
  id: UUID
  user?: { id: UUID; name?: string; username?: string } | null
  entityType: string
  entityId: UUID
  fieldsAccessed: string | null
  accessReason: string | null
  ipAddress?: string | null
  createdAt: ISODateTime
}
