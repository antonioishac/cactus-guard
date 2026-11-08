import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { listScreeningByStatus, listScreeningLists, reviewScreening, runScreening } from '@/api/screening'
import { addWatchlistEntry, deactivateWatchlistEntry, listWatchlist } from '@/api/watchlist'
import type { PageParams, UUID, WatchlistCreate } from '@/api/types'

export const useHitQueue = (status: string, paging: PageParams) =>
  useQuery({ queryKey: ['screening', 'queue', status, paging], queryFn: () => listScreeningByStatus(status, paging), placeholderData: keepPreviousData })

export const useScreeningLists = () =>
  useQuery({ queryKey: ['screening', 'lists'], queryFn: listScreeningLists, retry: false })

export const useWatchlist = () => useQuery({ queryKey: ['watchlist'], queryFn: listWatchlist, retry: false })

function useInvalidating<V, R>(fn: (v: V) => Promise<R>, successMessage: string, extra: unknown[][] = []) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    meta: { successMessage },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['screening'] })
      extra.forEach((k) => void qc.invalidateQueries({ queryKey: k }))
    },
  })
}

export const useRunScreening = () =>
  useInvalidating((v: { partyId: UUID; lists: string[] }) => runScreening(v.partyId, v.lists), 'Screening executado.', [['party']])

/** Confirmar hit pode acionar bloqueio CSNU e recálculo de risco: invalida a ficha da parte também. */
export const useReviewHit = () =>
  useInvalidating(
    (v: { id: UUID; status: 'CONFIRMED' | 'FALSE_POSITIVE'; justification: string }) => reviewScreening(v.id, v.status, v.justification),
    'Revisão registrada.',
    [['party'], ['parties'], ['alerts'], ['dashboard']],
  )

export const useAddWatchlist = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (b: WatchlistCreate) => addWatchlistEntry(b),
    meta: { successMessage: 'Registro incluído na lista interna.' },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['watchlist'] }),
  })
}

export const useDeactivateWatchlist = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: UUID) => deactivateWatchlistEntry(id),
    meta: { successMessage: 'Registro desativado.' },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['watchlist'] }),
  })
}
