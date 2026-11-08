import type { ISODateTime, UUID } from './common'

/** KycProcessResponse do backend. */
export interface KycProcess {
  id: UUID
  partyId: UUID
  partyName: string | null
  type: string
  status: string
  riskLevel: string | null
  startedAt: ISODateTime | null
  completedAt: ISODateTime | null
  expiresAt: ISODateTime | null
  createdAt: ISODateTime
}

export interface KycChecklistItem {
  id: UUID
  processId: UUID
  itemCode: string
  itemDescription: string
  required: boolean
  completed: boolean
  completedAt: ISODateTime | null
}

/** KycDocumentResponse (sem tamanho/content-type no DTO). */
export interface KycDocument {
  id: UUID
  processId: UUID
  documentType: string
  fileName: string
  status: string
  createdAt: ISODateTime
}

export const KYC_DOCUMENT_TYPES = [
  'RG',
  'CNH',
  'CPF',
  'COMPROVANTE_RESIDENCIA',
  'CONTRATO_SOCIAL',
  'CARTAO_CNPJ',
  'COMPROVANTE_RENDA',
  'OUTROS',
] as const
