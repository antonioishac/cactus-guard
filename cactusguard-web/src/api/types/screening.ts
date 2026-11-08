import type { ISODateTime, UUID } from './common'

export const SCREENING_STATUSES = ['PENDING', 'CONFIRMED', 'FALSE_POSITIVE'] as const
export type ScreeningStatus = (typeof SCREENING_STATUSES)[number]

/** ScreeningResultResponse (backend). */
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
  partyName: string | null
  requiresAssetFreeze: boolean
  reviewJustification: string | null
  reviewedAt: ISODateTime | null
  reviewedByName: string | null
}

/** POST /screening/{id}/review (JSON). Justificativa >= 20 caracteres não brancos. */
export interface ScreeningReviewRequest {
  status: 'CONFIRMED' | 'FALSE_POSITIVE'
  justification: string
}

/** GET /screening/lists: catálogo real. `code` é o valor enviado em `lists`. */
export interface ScreeningListInfo {
  code: string
  name: string
  type: string
  source: string | null
  requiresAssetFreeze: boolean
  active: boolean
  lastUpdated: ISODateTime | null
}

/** WatchlistEntry (lista interna). `document` pode vir mascarado. O backend ainda serializa a entity. */
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
