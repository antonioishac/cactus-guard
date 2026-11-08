import type { ISODateTime, UUID } from './common'
import type { Severity } from './alerts'

/**
 * Status do CaseService. `CLOSED` não é usado: encerramento = ARCHIVED ou COMMUNICATED.
 * `COMMUNICATE` = decisão de comunicar aprovada (aguarda envio ao COAF).
 */
export type CaseStatus = 'OPEN' | 'IN_ANALYSIS' | 'ESCALATED' | 'PENDING_APPROVAL' | 'COMMUNICATE' | 'COMMUNICATED' | 'ARCHIVED'

export type CaseDecision = 'COMMUNICATE' | 'ARCHIVE'

/** Calculado no backend pelo prazo regulatório vigente; `null` em case encerrado. */
export type SlaStatus = 'ON_TIME' | 'DUE_SOON' | 'OVERDUE'

/** CaseResponse (backend). */
export interface Case {
  id: UUID
  /** CG-AAAA-NNNNNN */
  caseNumber: string | null
  alertId: UUID | null
  partyId: UUID | null
  partyName: string | null
  /** nível de risco da parte */
  partyRiskLevel: string | null
  status: CaseStatus
  priority: Severity | string
  assignedToId: UUID | null
  decision: CaseDecision | null
  decisionJustification: string | null
  decidedById: UUID | null
  decidedAt: ISODateTime | null
  slaDueDate: ISODateTime | null
  slaStatus: SlaStatus | null
  occurredAt: ISODateTime | null
  selectionDeadline: ISODateTime | null
  selectedAt: ISODateTime | null
  analysisDeadline: ISODateTime | null
  communicationDeadline: ISODateTime | null
  escalationLevel: number
  edd: boolean
  /** indícios Carta 4.001 */
  indicatorCodes: string[] | null
  totalAmountInvolved: number | null
  approvedById: UUID | null
  approvedAt: ISODateTime | null
  closedAt: ISODateTime | null
  createdAt: ISODateTime
}

/** Múltiplos status vão separados por vírgula. */
export interface CaseFilters {
  status?: string
  assignedTo?: UUID
  slaStatus?: SlaStatus
  /** LOW|MEDIUM|HIGH|CRITICAL (nível de risco da parte) ou PEP */
  riskCategory?: string
  isEdd?: boolean
  from?: string
  to?: string
  partyId?: UUID
}

/** POST /cases (substitui o alias legado /cases/from-alert/{id}). */
export interface CaseCreateRequest {
  alertId: UUID
  /** >= 20 caracteres não brancos */
  justification: string
}

export interface CaseDecisionRequest {
  decision: CaseDecision
  /** obrigatória, mínimo 20 caracteres não brancos */
  justification: string
}

export interface CaseEscalationRequest {
  justification: string
  escalatedToId?: UUID
}

export interface CaseApprovalRequest {
  comment?: string
}

/** Rejeição da aprovação (quatro olhos): o case volta a IN_ANALYSIS com a decisão pendente limpa. */
export interface CaseRejectApprovalRequest {
  justification: string
}

export interface CaseNote {
  id: UUID
  authorId: UUID
  authorName: string
  text: string
  createdAt: ISODateTime
}

export interface CaseEvidence {
  id: UUID
  fileName: string
  contentType: string
  sizeBytes: number
  sha256: string
  description: string | null
  uploadedById: UUID
  uploadedByName: string
  uploadedAt: ISODateTime
}
