import { api } from './client'
import { pageQuery } from './params'
import type { Page, PageParams, ScreeningListInfo, ScreeningResult, ScreeningReviewRequest, UUID } from './types'

/** Listas aceitas pelo backend em `lists` (default PEP,SANCTIONS). */
export const DEFAULT_LISTS = ['PEP', 'SANCTIONS']

export async function runScreening(partyId: UUID, lists: string[]): Promise<ScreeningResult> {
  return (await api.post<ScreeningResult>(`/screening/screen/${partyId}`, null, { params: { lists: lists.join(',') } })).data
}

/** Fila de hits por status (default do backend: PENDING). */
export async function listScreeningByStatus(status: string, paging: PageParams): Promise<Page<ScreeningResult>> {
  return (await api.get<Page<ScreeningResult>>('/screening', { params: { status, ...pageQuery(paging) } })).data
}

/** Revisão em JSON (a justificativa não vai à URL). CONFIRMED em lista CSNU bloqueia a parte (Lei 13.810). */
export async function reviewScreening(id: UUID, body: ScreeningReviewRequest): Promise<ScreeningResult> {
  return (await api.post<ScreeningResult>(`/screening/${id}/review`, body)).data
}

/** Catálogo real das listas de screening. */
export async function listScreeningLists(): Promise<ScreeningListInfo[]> {
  return (await api.get<ScreeningListInfo[]>('/screening/lists')).data
}
