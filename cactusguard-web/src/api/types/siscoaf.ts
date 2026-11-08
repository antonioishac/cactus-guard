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

/** SiscoafResponse do backend. */
export interface SiscoafCommunication {
  id: UUID
  caseId: UUID | null
  partyId: UUID | null
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
  cancelledAt: ISODateTime | null
  payload: Record<string, unknown> | null
  createdAt: ISODateTime
  /* ---- NÃO existem no SiscoafResponse real (campos da entity/spec; opcionais) ---- */
  /** indícios Carta 4.001 (entity.indicatorCodes não é exposta no DTO) */
  indicatorCodes?: string[] | null
  partyName?: string | null
  cancelReason?: string | null
  /** período de referência da operação */
  periodFrom?: ISODate | null
  periodTo?: ISODate | null
  statusHistory?: SiscoafHistoryEntry[] | null
}

export interface SiscoafHistoryEntry {
  status: string
  at: ISODateTime
  byName?: string | null
  reason?: string | null
}

export interface SiscoafFilters {
  /** backend: obrigatório na prática (default DRAFT); aceita um único status */
  status?: string
  /** PROPOSTO: backend ignora hoje */
  type?: string
}

/** Backend: `reason` obrigatório (cancelar/retificar) e `payload` opcional (retificar). */
export interface SiscoafReasonRequest {
  reason: string
  payload?: Record<string, unknown>
}
