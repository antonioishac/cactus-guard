import type { ISODateTime, UUID } from './common'
import type { Severity } from './alerts'

/**
 * Status reais do CaseService: OPEN, ESCALATED, PENDING_APPROVAL, COMMUNICATE (aprovado p/ comunicar),
 * ARCHIVED. A spec 033 fala em IN_ANALYSIS e CLOSED; mantidos na união para tolerar a convergência.
 */
export type CaseStatus =
  | 'OPEN'
  | 'IN_ANALYSIS'
  | 'ESCALATED'
  | 'PENDING_APPROVAL'
  | 'COMMUNICATE'
  | 'ARCHIVED'
  | 'CLOSED'

export type CaseDecision = 'COMMUNICATE' | 'ARCHIVE'

export type SlaStatus = 'ON_TIME' | 'DUE_SOON' | 'OVERDUE'

export interface Case {
  id: UUID
  alertId: UUID | null
  partyId: UUID | null
  partyName: string | null
  status: CaseStatus
  priority: Severity | string
  assignedToId: UUID | null
  decision: CaseDecision | null
  decisionJustification: string | null
  decidedAt: ISODateTime | null
  slaDueDate: ISODateTime | null
  occurredAt: ISODateTime | null
  selectedAt: ISODateTime | null
  analysisDeadline: ISODateTime | null
  communicationDeadline: ISODateTime | null
  escalationLevel: number
  edd: boolean
  approvedById: UUID | null
  approvedAt: ISODateTime | null
  closedAt: ISODateTime | null
  createdAt: ISODateTime
  /** NÃO existe no CaseResponse real. Necessário p/ ocultar "Aprovar" ao decisor (spec 025). */
  decidedById?: UUID | null
  /** spec 033 (filtro slaStatus) — ainda não retornado */
  slaStatus?: SlaStatus
  caseNumber?: string | null
  /** indícios Carta 4.001 / códigos de regra (entity Case.indicatorCodes) */
  indicatorCodes?: string[] | null
  totalAmountInvolved?: number | null
}

export interface CaseFilters {
  status?: CaseStatus
  assignedTo?: UUID
  slaStatus?: SlaStatus
  riskCategory?: string
  from?: string
  to?: string
  partyId?: UUID
}

export interface CaseDecisionRequest {
  decision: CaseDecision
  /** obrigatória, mínimo 20 caracteres não brancos (Justifications.require) */
  justification: string
}

/** Backend: `justification` (obrigatória) + `escalatedToId`. Spec 033: `reason` + `targetRole`. */
export interface CaseEscalationRequest {
  justification: string
  escalatedToId?: UUID
}

/** Backend: `{ comment }` opcional. Spec 033: `{ approved, justification }` (rejeição ainda não existe). */
export interface CaseApprovalRequest {
  comment?: string
}

/* ---- Itens do dossiê sem endpoint no backend ainda (contrato proposto) ---- */

export interface CaseNote {
  id: UUID
  authorName: string
  text: string
  createdAt: ISODateTime
}

export interface CaseEvidence {
  id: UUID
  fileName: string
  sizeBytes: number
  sha256: string
  uploadedByName: string
  uploadedAt: ISODateTime
}
