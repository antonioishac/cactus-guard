import { api } from './client'
import type { UUID, WatchlistCreate, WatchlistEntry } from './types'

/** AUDITOR/ADMIN/SUPERVISOR. DIVERGÊNCIA: sem paginação no backend (lista todos os ativos). */
export async function listWatchlist(): Promise<WatchlistEntry[]> {
  return (await api.get<WatchlistEntry[]>('/watchlist')).data
}

/** Backend lê @RequestParam; enviamos como form no corpo para não expor documento/nome na URL. */
export async function addWatchlistEntry(b: WatchlistCreate): Promise<WatchlistEntry> {
  const form = new URLSearchParams({ document: b.document, name: b.name, reason: b.reason, severity: b.severity })
  if (b.expiresAt) form.set('expiresAt', b.expiresAt)
  return (await api.post<WatchlistEntry>('/watchlist', form)).data
}

export async function deactivateWatchlistEntry(id: UUID): Promise<WatchlistEntry> {
  return (await api.post<WatchlistEntry>(`/watchlist/${id}/deactivate`)).data
}
