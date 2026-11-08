import type { ISODateTime, UUID } from './common'

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type AlertStatus = 'OPEN' | 'IN_ANALYSIS' | 'ESCALATED' | 'FALSE_POSITIVE' | 'CLOSED'

/** AlertResponse (backend). Campos opcionais ainda não existem no DTO real. */
export interface Alert {
  id: UUID
  partyId: UUID | null
  partyName: string | null
  type: string
  severity: Severity
  status: AlertStatus
  title: string | null
  description: string | null
  ruleCode: string | null
  ruleVersion: number | null
  assignedToId: UUID | null
  dueDate: ISODateTime | null
  occurredAt: ISODateTime | null
  /** occurredAt + 45 dias (spec 046 §6) */
  selectionDeadline: ISODateTime | null
  totalAmount: number | null
  falsePositiveReason: string | null
  createdAt: ISODateTime
  /** spec 033: documento mascarado da parte (não existe no DTO real) */
  partyDocument?: string | null
  /** spec 046 §6 */
  cc4001Indicator?: string | null
}

/** Uma linha de evidência: parâmetro da regra vs. valor observado (spec 046 §6 evidence_snapshot). */
export interface AlertEvidenceItem {
  parameter: string
  label?: string
  threshold?: string | number | null
  observed: string | number | null
}

export interface AlertHistoryEntry {
  id: UUID
  action: string
  fromStatus: string | null
  toStatus: string | null
  actorName: string | null
  justification: string | null
  createdAt: ISODateTime
}

/**
 * Detalhe do alerta. O backend hoje devolve só `Alert`; evidências, transações vinculadas e histórico
 * (spec 033 "detalhe completo") são opcionais até o endpoint real expô-los.
 */
export interface AlertDetail extends Alert {
  evidence?: AlertEvidenceItem[] | null
  triggeringTransactionIds?: UUID[] | null
  history?: AlertHistoryEntry[] | null
}

export interface AlertFilters {
  status?: AlertStatus
  severity?: Severity
  type?: string
  assignedTo?: UUID
  from?: string
  to?: string
  partyId?: UUID
}

export interface FalsePositiveRequest {
  /** máx. 100 caracteres */
  reason: string
  justification: string
}

/** Spec 033 prevê POST /alerts/{id}/escalate; ainda não existe no backend. Corpo é proposta nossa. */
export interface AlertEscalateRequest {
  justification: string
}
