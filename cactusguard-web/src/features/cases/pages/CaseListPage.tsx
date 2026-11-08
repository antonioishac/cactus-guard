import { Link } from 'react-router'
import type { Case, CaseFilters, CaseStatus, SlaStatus } from '@/api/types'
import { Badge, SeverityBadge, StatusBadge } from '@/components/ui/Badge'
import { DataTable } from '@/components/ui/DataTable'
import type { Column } from '@/components/ui/DataTable'
import { SlaBadge } from '@/components/ui/SlaBadge'
import { FilterBar, SelectFilter, TextFilter } from '@/components/ui/Filters'
import { PageHeader } from '@/components/ui/Layout'
import { useListState } from '@/hooks/useListState'
import { severityLabel, statusLabel } from '@/lib/format'
import { useAuth } from '@/features/auth/useAuth'
import { useCases } from '../hooks'
import { currentDeadline } from '../ui-rules'

const KEYS = ['status', 'assignedTo', 'slaStatus', 'riskCategory', 'isEdd', 'from', 'to', 'partyId'] as const
const STATUSES: CaseStatus[] = ['OPEN', 'IN_ANALYSIS', 'ESCALATED', 'PENDING_APPROVAL', 'COMMUNICATE', 'COMMUNICATED', 'ARCHIVED']
const SLA: { value: SlaStatus; label: string }[] = [
  { value: 'ON_TIME', label: 'No prazo' },
  { value: 'DUE_SOON', label: 'Vencendo' },
  { value: 'OVERDUE', label: 'Vencido' },
]

const columns: Column<Case>[] = [
  {
    key: 'case',
    header: 'Case',
    render: (c) => (
      <Link to={`/casos/${c.id}`} className="font-medium text-brand underline-offset-2 hover:underline">
        {c.caseNumber ?? `#${c.id.slice(0, 8)}`}
      </Link>
    ),
  },
  { key: 'party', header: 'Parte', render: (c) => c.partyName ?? '—' },
  {
    key: 'status',
    header: 'Status',
    sortField: 'status',
    render: (c) => (
      <span className="inline-flex flex-wrap items-center gap-1">
        <StatusBadge status={c.status} />
        {c.edd && <Badge tone="warning">EDD</Badge>}
      </span>
    ),
  },
  { key: 'priority', header: 'Prioridade', sortField: 'priority', render: (c) => <SeverityBadge severity={String(c.priority)} /> },
  { key: 'risk', header: 'Risco da parte', render: (c) => (c.partyRiskLevel ? severityLabel(c.partyRiskLevel) : '—') },
  {
    key: 'sla',
    header: 'Prazo vigente (SLA)',
    sortField: 'analysisDeadline',
    render: (c) => {
      const cur = currentDeadline(c)
      return <SlaBadge slaStatus={c.slaStatus} deadline={cur.deadline} phase={cur.phase} />
    },
  },
]

export function CaseListPage() {
  const { user } = useAuth()
  const list = useListState(KEYS, 'analysisDeadline,asc')
  const f = list.filters
  const filters: CaseFilters = {
    status: (f.status || undefined) as CaseStatus | undefined,
    assignedTo: f.assignedTo || undefined,
    slaStatus: (f.slaStatus || undefined) as SlaStatus | undefined,
    riskCategory: f.riskCategory || undefined,
    isEdd: f.isEdd === 'true' ? true : undefined,
    from: f.from || undefined,
    to: f.to || undefined,
    partyId: f.partyId || undefined,
  }
  const q = useCases(filters, { page: list.page, size: list.size, sort: list.sort })

  return (
    <>
      <PageHeader title="Casos" subtitle="Cases em análise, com prazos regulatórios (Circular BCB 3.978)" />
      <FilterBar onClear={list.clear} hasActive={list.hasActive}>
        <SelectFilter label="Status" value={f.status} onChange={(v) => list.setFilter('status', v)} options={STATUSES.map((s) => ({ value: s, label: statusLabel(s) }))} />
        <SelectFilter label="Prazo (SLA)" value={f.slaStatus} onChange={(v) => list.setFilter('slaStatus', v)} options={SLA} />
        <SelectFilter label="Risco / prioridade" value={f.riskCategory} onChange={(v) => list.setFilter('riskCategory', v)} options={[...['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) => ({ value: s, label: severityLabel(s) })), { value: 'PEP', label: 'PEP' }]} />
        <SelectFilter label="EDD" value={f.isEdd} onChange={(v) => list.setFilter('isEdd', v)} options={[{ value: 'true', label: 'Somente EDD' }]} />
        <SelectFilter label="Responsável" value={f.assignedTo} onChange={(v) => list.setFilter('assignedTo', v)} options={user ? [{ value: user.id, label: 'Somente meus cases' }] : []} />
        <TextFilter label="De" type="date" value={f.from} onChange={(v) => list.setFilter('from', v)} />
        <TextFilter label="Até" type="date" value={f.to} onChange={(v) => list.setFilter('to', v)} />
        <TextFilter label="ID da parte" value={f.partyId} onChange={(v) => list.setFilter('partyId', v.trim())} placeholder="UUID" />
      </FilterBar>
      <DataTable
        caption="Lista de cases"
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={() => void q.refetch()}
        rowKey={(c) => c.id}
        rowClassName={(c) => (c.status === 'PENDING_APPROVAL' ? 'bg-warning-bg/60 hover:bg-warning-bg' : 'hover:bg-canvas')}
        sort={list.sort}
        onSort={list.setSort}
        onPage={list.setPage}
        emptyTitle="Nenhum case encontrado"
        emptyHint={list.hasActive ? 'Ajuste ou limpe os filtros.' : 'Cases são abertos a partir de alertas.'}
      />
    </>
  )
}
