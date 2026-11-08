import { api } from './client'
import { pageQuery } from './params'
import type { Page, PageParams, ScreeningListInfo, ScreeningResult, UUID } from './types'

/** Listas aceitas pelo backend em `lists` (default PEP,SANCTIONS). */
export const DEFAULT_LISTS = ['PEP', 'SANCTIONS']

export async function runScreening(partyId: UUID, lists: string[]): Promise<ScreeningResult> {
  return (await api.post<ScreeningResult>(`/screening/screen/${partyId}`, null, { params: { lists: lists.join(',') } })).data
}

/** Fila de hits por status (default do backend: PENDING). */
export async function listScreeningByStatus(status: string, paging: PageParams): Promise<Page<ScreeningResult>> {
  return (await api.get<Page<ScreeningResult>>('/screening', { params: { status, ...pageQuery(paging) } })).data
}

/**
 * Revisão do hit. Backend: `POST /screening/{id}/review?status=`. A justificativa vai no corpo (form) para não
 * ir à URL; o backend já tem `review(id, status, justification)` no service, mas o controller ainda não a lê.
 */
export async function reviewScreening(id: UUID, status: 'CONFIRMED' | 'FALSE_POSITIVE', justification: string): Promise<ScreeningResult> {
  return (await api.post<ScreeningResult>(`/screening/${id}/review`, new URLSearchParams({ status, justification }))).data
}

/** PROPOSTO: catálogo de listas (a entity ScreeningList existe, sem controller). */
export async function listScreeningLists(): Promise<ScreeningListInfo[]> {
  return (await api.get<ScreeningListInfo[]>('/screening/lists')).data
}
