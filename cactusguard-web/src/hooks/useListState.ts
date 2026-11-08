import { useCallback } from 'react'
import { useSearchParams } from 'react-router'

export const PAGE_SIZE = 20

/**
 * Estado de listagem na URL (filtros, página, ordenação): permite compartilhar/voltar.
 * ATENÇÃO: só valores não-PII (status, severidade, UUIDs, datas). Nunca documento/nome.
 */
export function useListState<K extends string>(keys: readonly K[], defaultSort: string) {
  const [params, setParams] = useSearchParams()

  const filters = Object.fromEntries(keys.map((k) => [k, params.get(k) ?? ''])) as Record<K, string>
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
  const sort = params.get('sort') ?? defaultSort

  const update = useCallback(
    (mutate: (p: URLSearchParams) => void) => {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          mutate(next)
          return next
        },
        { replace: true },
      )
    },
    [setParams],
  )

  const setFilter = useCallback(
    (key: K, value: string) =>
      update((p) => {
        if (value) p.set(key, value)
        else p.delete(key)
        p.delete('page') // mudar filtro volta para a primeira página
      }),
    [update],
  )
  const setPage = useCallback((n: number) => update((p) => (n > 0 ? p.set('page', String(n)) : p.delete('page'))), [update])
  const setSort = useCallback((s: string) => update((p) => (p.set('sort', s), p.delete('page'))), [update])
  const clear = useCallback(() => update((p) => [...keys, 'page', 'sort'].forEach((k) => p.delete(k))), [update, keys])

  const hasActive = keys.some((k) => !!filters[k])
  return { filters, page, sort, size: PAGE_SIZE, setFilter, setPage, setSort, clear, hasActive }
}
