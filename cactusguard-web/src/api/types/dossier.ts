import type { ISODate, ISODateTime, UUID } from './common'
import type { AlertEvidenceItem, AlertPartySummary } from './alerts'
import type { Case, CaseEvidence, CaseNote } from './cases'
import type { TransactionSummary } from './transactions'

export interface DossierAlert {
  id: UUID
  type: string
  severity: string
  status: string
  title: string | null
  ruleCode: string | null
  ruleVersion: number | null
  evidence: AlertEvidenceItem[] | null
}

export interface DossierRiskItem {
  previousLevel: string | null
  newLevel: string
  previousScore: number | null
  newScore: number
  reason: string | null
  changedByName: string | null
  createdAt: ISODateTime
}

export interface DossierPep {
  pep: boolean
  status: string | null
  category: string | null
  role: string | null
  startDate: ISODate | null
  endDate: ISODate | null
  expiryDate: ISODate | null
  approvedById: UUID | null
  approvedByName: string | null
  approvedAt: ISODateTime | null
}

export interface DossierUbo {
  id: UUID
  name: string
  /** mascarado */
  document: string | null
  ownershipPct: number | null
  controlType: string | null
  ownerType: string | null
  pep: boolean
  finalBeneficiary: boolean
  indirect: boolean
  ownershipChain: Record<string, unknown>[] | null
}

export interface DossierScreeningHit {
  id: UUID
  listCode: string
  listType: string | null
  matchedName: string
  matchScore: number
  matchType: string
  status: string
  reviewJustification: string | null
  reviewedAt: ISODateTime | null
  createdAt: ISODateTime
}

export interface DossierAuditItem {
  action: string
  userId: UUID | null
  userName: string | null
  details: Record<string, unknown> | null
  createdAt: ISODateTime
}

/** GET /cases/{id}/dossier: PII sempre mascarada. */
export interface CaseDossier {
  caseInfo: Case
  alert: DossierAlert | null
  party: AlertPartySummary | null
  riskHistory: DossierRiskItem[] | null
  pep: DossierPep | null
  ubos: DossierUbo[] | null
  alertTransactions: TransactionSummary[] | null
  periodTransactions: TransactionSummary[] | null
  period: { from: ISODateTime; to: ISODateTime } | null
  screeningHits: DossierScreeningHit[] | null
  evidences: CaseEvidence[] | null
  notes: CaseNote[] | null
  auditTrail: DossierAuditItem[] | null
}
