import type { ISODate, ISODateTime, UUID } from './common'
import type { Page } from './common'

/** GET /approvals/pending (SUPERVISOR). `ownDecision`/`ownChange`: itens que o próprio usuário não pode aprovar. */
export interface PendingCaseItem {
  id: UUID
  caseNumber: string | null
  partyId: UUID | null
  partyName: string | null
  partyRiskLevel: string | null
  decision: string | null
  decidedById: UUID | null
  decidedAt: ISODateTime | null
  edd: boolean
  slaStatus: 'ON_TIME' | 'DUE_SOON' | 'OVERDUE' | null
  analysisDeadline: ISODateTime | null
  ownDecision: boolean
}

export interface PendingRuleItem {
  code: string
  name: string
  category: string
  severity: string
  version: number
  createdById: UUID | null
  createdByName: string | null
  updatedAt: ISODateTime | null
  ownChange: boolean
}

/** PEP vigente sem aprovação da alta administração. */
export interface PendingPepItem {
  partyId: UUID
  partyName: string
  /** mascarado */
  document: string | null
  pepCategory: string | null
  pepRole: string | null
  pepStartDate: ISODate | null
  riskLevel: string | null
  markedAt: ISODateTime | null
}

export interface PendingApprovals {
  cases: Page<PendingCaseItem>
  rules: Page<PendingRuleItem>
  peps: Page<PendingPepItem>
  counts: { cases: number; rules: number; peps: number }
}
