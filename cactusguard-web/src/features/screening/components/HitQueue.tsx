import { Link } from 'react-router'
import { SCREENING_STATUSES } from '@/api/types'
import type { ScreeningResult } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { DataTable } from '@/components/ui/DataTable'
import type { Column } from '@/components/ui/DataTable'
import { FilterBar, SelectFilter } from '@/components/ui/Filters'
import { useListState } from '@/hooks/useListState'
import { formatDate, maskDocument } from '@/lib/format'
import { HitReviewButtons, HitStatusBadge } from './HitReview'
import { HIT_STATUS_LABEL } from '../hitLabels'
import { useHitQueue } from '../hooks'

const columns: Column<ScreeningResult>[] = [
  {
    key: 'party',
    header: 'Parte',
    render: (h) => (
      <Link to={`/partes/${h.partyId}?aba=screening`} className="font-medium text-brand underline-offset-2 hover:underline">
        {h.partyName ?? `Parte #${h.partyId.slice(0, 8)}`}
      </Link>
    ),
  },
  { key: 'list', header: 'Lista', sortField: 'listCode', render: (h) => <span className="inline-flex items-center gap-1">{h.listCode}{h.requiresAssetFreeze && <Badge tone="danger">Bloqueio CSNU</Badge>}</span> },
  { key: 'matched', header: 'Nome encontrado', render: (h) => h.matchedName },
  { key: 'doc', header: 'Documento', render: (h) => <span className="font-mono">{maskDocument(h.matchedDocument)}</span> },
  { key: 'score', header: 'Score', sortField: 'matchScore', render: (h) => h.matchScore },
  { key: 'type', header: 'Tipo', render: (h) => h.matchType },
  { key: 'status', header: 'Status', render: (h) => <HitStatusBadge status={h.status} /> },
  { key: 'date', header: 'Data', sortField: 'createdAt', render: (h) => formatDate(h.createdAt) },
  { key: 'act', header: 'Revisão', render: (h) => <HitReviewButtons hit={h} /> },
]

const KEYS = ['status'] as const

export function HitQueue() {
  const list = useListState(KEYS, 'createdAt,asc')
  const status = list.filters.status || 'PENDING'
  const q = useHitQueue(status, { page: list.page, size: list.size, sort: list.sort })
  return (
    <>
      <FilterBar onClear={list.clear} hasActive={list.hasActive}>
        <SelectFilter label="Status" value={list.filters.status} onChange={(v) => list.setFilter('status', v)} options={SCREENING_STATUSES.map((s) => ({ value: s, label: HIT_STATUS_LABEL[s] ?? s }))} />
        <p className="pb-2 text-xs text-muted">Sem filtro, mostra os pendentes.</p>
      </FilterBar>
      <DataTable
        caption="Fila de hits de screening"
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={() => void q.refetch()}
        rowKey={(h) => h.id}
        sort={list.sort}
        onSort={list.setSort}
        onPage={list.setPage}
        emptyTitle={status === 'PENDING' ? 'Nenhum hit pendente de revisão' : 'Nenhum hit neste status'}
      />
    </>
  )
}
