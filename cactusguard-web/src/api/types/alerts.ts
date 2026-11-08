import type { ISODateTime, UUID } from './common'
import type { TransactionSummary } from './transactions'

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type AlertStatus = 'OPEN' | 'IN_ANALYSIS' | 'ESCALATED' | 'FALSE_POSITIVE' | 'CLOSED'

/** AlertResponse (backend). `partyDocument` já vem mascarado. */
export interface Alert {
  id: UUID
  partyId: UUID | null
  partyName: string | null
  partyDocument: string | null
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
}

/** Uma linha de evidência: parâmetro da regra vs. valor observado (snapshot do disparo). */
export interface AlertEvidenceItem {
  parameter: string
  label: string | null
  threshold: string | number | null
  observed: string | number | null
}

export interface AlertHistoryEntry {
  id: UUID
  action: string
  fromStatus: string | null
  toStatus: string | null
  actorId: UUID | null
  actorName: string | null
  justification: string | null
  createdAt: ISODateTime
}

export interface AlertRuleSummary {
  code: string
  name: string
  version: number
  category: string
  severity: string
  carta4001Reference: string | null
}

export interface AlertPartySummary {
  id: UUID
  name: string
  type: string
  /** mascarado */
  document: string | null
  riskLevel: string | null
  riskScore: number | null
  pep: boolean
  pepStatus: string | null
  eddRequired: boolean
  assetFreezeActive: boolean
  status: string
}

/** AlertDetailResponse: `AlertResponse` + evidências, transações, histórico, regra e parte. */
export interface AlertDetail extends Alert {
  evidence: AlertEvidenceItem[] | null
  /** mapa bruto do disparo */
  evidenceSnapshot: Record<string, unknown> | null
  triggeringTransactionIds: UUID[] | null
  transactions: TransactionSummary[] | null
  history: AlertHistoryEntry[] | null
  rule: AlertRuleSummary | null
  party: AlertPartySummary | null
}

/** Valores múltiplos vão separados por vírgula (o backend aceita repetidos ou CSV). */
export interface AlertFilters {
  status?: string
  severity?: string
  type?: string
  assignedTo?: UUID
  /** AAAA-MM-DD (fuso de Brasília); `to` é inclusivo até o fim do dia */
  from?: string
  to?: string
  partyId?: UUID
  ruleCode?: string
}

export interface FalsePositiveRequest {
  /** máx. 100 caracteres */
  reason: string
  justification: string
}

export interface AlertEscalateRequest {
  justification: string
}

export interface AlertComment {
  id: UUID
  authorId: UUID
  authorName: string
  text: string
  createdAt: ISODateTime
}
