import type { ReactNode } from 'react'
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight } from 'lucide-react'
import type { Page } from '@/api/types'
import { Button } from './Button'
import { EmptyState, ErrorState, Skeleton } from './Feedback'

export interface Column<T> {
  key: string
  header: string
  render: (row: T) => ReactNode
  /** campo Spring p/ ordenação; ausente = não ordenável */
  sortField?: string
  className?: string
}

interface Props<T> {
  columns: Column<T>[]
  page: Page<T> | undefined
  loading: boolean
  error?: unknown
  onRetry?: () => void
  rowKey: (row: T) => string
  /** `campo,asc|desc` */
  sort?: string
  onSort?: (sort: string) => void
  onPage: (page: number) => void
  caption: string
  emptyTitle: string
  emptyHint?: string
  rowClassName?: (row: T) => string
}

/** Tabela com paginação e ordenação no servidor. Página 0-based internamente, 1-based na tela. */
export function DataTable<T>({
  columns, page, loading, error, onRetry, rowKey, sort, onSort, onPage, caption, emptyTitle, emptyHint, rowClassName,
}: Props<T>) {
  const [sortField, sortDir] = (sort ?? '').split(',')

  function toggle(field: string) {
    onSort?.(`${field},${sortField === field && sortDir === 'asc' ? 'desc' : 'asc'}`)
  }

  return (
    <div className="overflow-hidden rounded-xl border border-line bg-surface">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <caption className="sr-only">{caption}</caption>
          <thead className="bg-neutral-bg text-xs uppercase text-muted">
            <tr>
              {columns.map((c) => {
                const active = c.sortField && sortField === c.sortField
                return (
                  <th
                    key={c.key}
                    scope="col"
                    aria-sort={active ? (sortDir === 'asc' ? 'ascending' : 'descending') : undefined}
                    className={`px-3 py-2 font-medium ${c.className ?? ''}`}
                  >
                    {c.sortField && onSort ? (
                      <button onClick={() => toggle(c.sortField!)} className="inline-flex items-center gap-1 uppercase hover:text-ink">
                        {c.header}
                        {active && (sortDir === 'asc' ? <ArrowUp size={12} aria-hidden /> : <ArrowDown size={12} aria-hidden />)}
                      </button>
                    ) : (
                      c.header
                    )}
                  </th>
                )
              })}
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {loading &&
              Array.from({ length: 6 }, (_, i) => (
                <tr key={i}>
                  {columns.map((c) => (
                    <td key={c.key} className="px-3 py-3">
                      <Skeleton className="h-4 w-full" />
                    </td>
                  ))}
                </tr>
              ))}
            {!loading &&
              page?.content.map((row) => (
                <tr key={rowKey(row)} className={rowClassName?.(row) ?? 'hover:bg-canvas'}>
                  {columns.map((c) => (
                    <td key={c.key} className={`px-3 py-2.5 ${c.className ?? ''}`}>
                      {c.render(row)}
                    </td>
                  ))}
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {!loading && !!error && <ErrorState error={error} onRetry={onRetry} />}
      {!loading && !error && page && page.content.length === 0 && <EmptyState title={emptyTitle} hint={emptyHint} />}

      {page && page.totalElements > 0 && (
        <nav aria-label="Paginação" className="flex flex-wrap items-center justify-between gap-2 border-t border-line px-3 py-2 text-sm">
          <span className="text-muted">
            Página {page.number + 1} de {Math.max(page.totalPages, 1)} · {page.totalElements.toLocaleString('pt-BR')} registros
          </span>
          <div className="flex gap-1">
            <Button variant="secondary" className="!px-2 !py-1" disabled={page.number <= 0} onClick={() => onPage(page.number - 1)} aria-label="Página anterior">
              <ChevronLeft size={16} />
            </Button>
            <Button variant="secondary" className="!px-2 !py-1" disabled={page.number + 1 >= page.totalPages} onClick={() => onPage(page.number + 1)} aria-label="Próxima página">
              <ChevronRight size={16} />
            </Button>
          </div>
        </nav>
      )}
    </div>
  )
}
