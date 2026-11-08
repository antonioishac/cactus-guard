import type { ISODate, ISODateTime, UUID } from './common'

/**
 * Tipos reais do backend: COS, COE, SAR (legado de COS), COAF_COMMUNICATION, NEGATIVE_DECLARATION.
 * A UI trata SAR como COS (legado).
 */
export type SiscoafType = 'COS' | 'COE' | 'SAR' | 'COAF_COMMUNICATION' | 'NEGATIVE_DECLARATION'

/** Status vistos no SiscoafService; string aberta porque o backend não usa enum. */
export type SiscoafStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'SUBMITTED'
  | 'ACKNOWLEDGED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'RECTIFIED'

/** Linha de `siscoaf_history` (só no detalhe). */
export interface SiscoafHistoryEntry {
  action: string
  status: string
  fromStatus: string | null
  byId: UUID | null
  byName: string | null
  reason: string | null
  at: ISODateTime
}

/** SiscoafResponse do backend. */
export interface SiscoafCommunication {
  id: UUID
  caseId: UUID | null
  caseNumber: string | null
  partyId: UUID | null
  partyName: string | null
  transactionId: UUID | null
  originalCommunicationId: UUID | null
  type: SiscoafType | string
  status: SiscoafStatus | string
  protocolNumber: string | null
  submittedAt: ISODateTime | null
  /** prazo de envio = dia útil seguinte à decisão (BusinessCalendar no backend) */
  deadlineAt: ISODateTime | null
  decidedAt: ISODateTime | null
  referenceYear: number | null
  amount: number | null
  /** indícios Carta 4.001 */
  indicatorCodes: string[] | null
  periodFrom: ISODate | null
  periodTo: ISODate | null
  cancelledAt: ISODateTime | null
  cancelReason: string | null
  createdById: UUID | null
  approvedById: UUID | null
  approvedAt: ISODateTime | null
  /** PEP, risco alto/crítico ou EDD: aprovador != decisor e != criador; submit de DRAFT é bloqueado sem aprovação */
  fourEyesRequired: boolean
  payload: Record<string, unknown> | null
  createdAt: ISODateTime
  /** só em GET /siscoaf/{id} */
  statusHistory?: SiscoafHistoryEntry[] | null
}

/** `status` e `type` aceitam vários valores (CSV). Sem `status` = todos (não assumir DRAFT). */
export interface SiscoafFilters {
  status?: string
  type?: string
  from?: string
  to?: string
  partyId?: UUID
  caseId?: UUID
}

/** `reason` obrigatório (cancelar/retificar) e `payload` opcional (retificar). */
export interface SiscoafReasonRequest {
  reason: string
  payload?: Record<string, unknown>
}

/** POST /siscoaf/{id}/approve: `comment` (ou `justification`) >= 20 não brancos. */
export interface SiscoafApproveRequest {
  comment: string
}

/** POST /siscoaf/{id}/submit: `reason` opcional (>= 20 se informado). */
export interface SiscoafSubmitRequest {
  reason?: string
}
