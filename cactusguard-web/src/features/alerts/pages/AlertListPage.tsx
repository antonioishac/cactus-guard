import { Link } from 'react-router'
import type { AlertFilters, AlertStatus, Severity, Alert } from '@/api/types'
import { SeverityBadge, StatusBadge } from '@/components/ui/Badge'
import { DataTable } from '@/components/ui/DataTable'
import type { Column } from '@/components/ui/DataTable'
import { DeadlineBadge } from '@/components/ui/DeadlineBadge'
import { FilterBar, SelectFilter, TextFilter } from '@/components/ui/Filters'
import { PageHeader } from '@/components/ui/Layout'
import { useListState } from '@/hooks/useListState'
import { formatBRL, formatDateTime, severityLabel, statusLabel } from '@/lib/format'
import { useAuth } from '@/features/auth/useAuth'
import { useAlerts } from '../hooks'

const KEYS = ['status', 'severity', 'type', 'assignedTo', 'from', 'to', 'partyId', 'ruleCode'] as const
const STATUSES: AlertStatus[] = ['OPEN', 'IN_ANALYSIS', 'ESCALATED', 'FALSE_POSITIVE', 'CLOSED']
const SEVERITIES: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
const TYPES = ['TRANSACTIONAL', 'PEP', 'SANCTIONS', 'WATCHLIST', 'BEHAVIORAL']

const columns: Column<Alert>[] = [
  { key: 'severity', header: 'Severidade', sortField: 'severity', render: (a) => <SeverityBadge severity={a.severity} /> },
  {
    key: 'title',
    header: 'Alerta',
    render: (a) => (
      <Link to={`/alertas/${a.id}`} className="font-medium text-brand underline-offset-2 hover:underline">
        {a.title ?? a.type}
        <span className="block text-xs font-normal text-muted">{a.type}</span>
      </Link>
    ),
  },
  { key: 'party', header: 'Parte', render: (a) => a.partyName ?? '—' },
  { key: 'rule', header: 'Regra', render: (a) => (a.ruleCode ? `${a.ruleCode}${a.ruleVersion ? ` v${a.ruleVersion}` : ''}` : '—') },
  { key: 'amount', header: 'Valor', sortField: 'totalAmount', className: 'text-right tabular-nums', render: (a) => formatBRL(a.totalAmount) },
  { key: 'status', header: 'Status', sortField: 'status', render: (a) => <StatusBadge status={a.status} /> },
  {
    key: 'selection',
    header: 'Prazo de seleção',
    sortField: 'selectionDeadline',
    render: (a) =>
      a.status === 'OPEN' || a.status === 'IN_ANALYSIS' || a.status === 'ESCALATED' ? (
        <DeadlineBadge stage="selection" deadline={a.selectionDeadline} />
      ) : (
        <span className="text-xs text-muted">—</span>
      ),
  },
  { key: 'occurred', header: 'Ocorrência', sortField: 'occurredAt', render: (a) => formatDateTime(a.occurredAt) },
]

export function AlertListPage() {
  const { user } = useAuth()
  const list = useListState(KEYS, 'createdAt,desc')
  const f = list.filters
  const filters: AlertFilters = {
    status: (f.status || undefined) as AlertStatus | undefined,
    severity: (f.severity || undefined) as Severity | undefined,
    type: f.type || undefined,
    assignedTo: f.assignedTo || undefined,
    from: f.from || undefined,
    to: f.to || undefined,
    partyId: f.partyId || undefined,
    ruleCode: f.ruleCode || undefined,
  }
  const q = useAlerts(filters, { page: list.page, size: list.size, sort: list.sort })

  return (
    <>
      <PageHeader title="Alertas" subtitle="Fila de alertas de monitoramento para análise" />
      <FilterBar onClear={list.clear} hasActive={list.hasActive}>
        <SelectFilter label="Status" value={f.status} onChange={(v) => list.setFilter('status', v)} options={STATUSES.map((s) => ({ value: s, label: statusLabel(s) }))} />
        <SelectFilter label="Severidade" value={f.severity} onChange={(v) => list.setFilter('severity', v)} options={SEVERITIES.map((s) => ({ value: s, label: severityLabel(s) }))} />
        <SelectFilter label="Tipo" value={f.type} onChange={(v) => list.setFilter('type', v)} options={TYPES.map((t) => ({ value: t, label: t }))} />
        <SelectFilter label="Responsável" value={f.assignedTo} onChange={(v) => list.setFilter('assignedTo', v)} options={user ? [{ value: user.id, label: 'Somente meus alertas' }] : []} />
        <TextFilter label="De" type="date" value={f.from} onChange={(v) => list.setFilter('from', v)} />
        <TextFilter label="Até" type="date" value={f.to} onChange={(v) => list.setFilter('to', v)} />
        <TextFilter label="ID da parte" value={f.partyId} onChange={(v) => list.setFilter('partyId', v.trim())} placeholder="UUID" />
        <TextFilter label="Código da regra" value={f.ruleCode} onChange={(v) => list.setFilter('ruleCode', v.trim())} placeholder="Ex.: STRUCTURING" />
      </FilterBar>
      <DataTable
        caption="Lista de alertas"
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={() => void q.refetch()}
        rowKey={(a) => a.id}
        rowClassName={(a) => (a.severity === 'CRITICAL' && a.status === 'OPEN' ? 'bg-danger-bg/50 hover:bg-danger-bg' : 'hover:bg-canvas')}
        sort={list.sort}
        onSort={list.setSort}
        onPage={list.setPage}
        emptyTitle="Nenhum alerta encontrado"
        emptyHint={list.hasActive ? 'Ajuste ou limpe os filtros para ver mais resultados.' : 'Não há alertas no momento.'}
      />
    </>
  )
}
