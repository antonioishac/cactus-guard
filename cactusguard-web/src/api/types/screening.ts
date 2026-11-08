import type { ISODateTime, UUID } from './common'

export const SCREENING_STATUSES = ['PENDING', 'CONFIRMED', 'FALSE_POSITIVE'] as const
export type ScreeningStatus = (typeof SCREENING_STATUSES)[number]

/** ScreeningResultResponse do backend. `partyName`/`requiresAssetFreeze`/revisão são PROPOSTOS (opcionais). */
export interface ScreeningResult {
  id: UUID
  partyId: UUID
  listCode: string
  matchedName: string
  matchedDocument: string | null
  matchScore: number
  matchType: string
  status: string
  createdAt: ISODateTime
  partyName?: string | null
  requiresAssetFreeze?: boolean
  reviewJustification?: string | null
  reviewedAt?: ISODateTime | null
  reviewedByName?: string | null
}

/** Listas de screening (spec 011). `code` é o valor enviado em `lists`. Endpoint de catálogo é PROPOSTO. */
export interface ScreeningListInfo {
  code: string
  name: string
  type: string
  source: string | null
  requiresAssetFreeze: boolean
  active: boolean
  lastUpdated: ISODateTime | null
}

/** Entity WatchlistEntry (lista interna) serializada direto. `document` pode vir mascarado. */
export interface WatchlistEntry {
  id: UUID
  document: string
  name: string
  reason: string
  severity: string
  active: boolean
  createdAt: ISODateTime
  expiresAt: ISODateTime | null
}

export interface WatchlistCreate {
  document: string
  name: string
  reason: string
  severity: string
  expiresAt?: ISODateTime
}
