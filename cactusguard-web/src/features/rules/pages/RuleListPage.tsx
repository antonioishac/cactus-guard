import { Link } from 'react-router'
import type { MonitoringRule } from '@/api/types'
import { SeverityBadge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { DataTable } from '@/components/ui/DataTable'
import type { Column } from '@/components/ui/DataTable'
import { FilterBar, SelectFilter, TextFilter } from '@/components/ui/Filters'
import { PageHeader } from '@/components/ui/Layout'
import { useAuth } from '@/features/auth/useAuth'
import { useListState } from '@/hooks/useListState'
import { formatDate, formatPercent, severityLabel } from '@/lib/format'
import { RunEngineButton } from '../components/RuleActions'
import { RuleStatusBadge } from '../components/Shared'
import { useRules } from '../hooks'
import { CATEGORY_LABEL, RULE_STATUS_LABEL, categoryLabel } from '../ui-rules'

const KEYS = ['status', 'category', 'severity', 'q'] as const

const columns: Column<MonitoringRule>[] = [
  {
    key: 'code',
    header: 'Regra',
    sortField: 'code',
    render: (r) => (
      <Link to={`/regras/${encodeURIComponent(r.code)}`} className="font-medium text-brand underline-offset-2 hover:underline">
        {r.code}
        <span className="block text-xs font-normal text-ink">{r.name}</span>
      </Link>
    ),
  },
  { key: 'carta', header: 'Cenário (Carta 4.001)', render: (r) => <span className="text-xs">{r.carta4001Reference ?? '—'}</span> },
  { key: 'category', header: 'Categoria', sortField: 'category', render: (r) => categoryLabel(r.category) },
  { key: 'severity', header: 'Severidade', sortField: 'severity', render: (r) => <SeverityBadge severity={r.severity} /> },
  { key: 'status', header: 'Status', sortField: 'status', render: (r) => <RuleStatusBadge status={r.status} /> },
  { key: 'version', header: 'Versão', sortField: 'version', render: (r) => `v${r.version}` },
  { key: 'alerts', header: 'Alertas (30 d)', className: 'text-right tabular-nums', render: (r) => (r.alertsGenerated != null ? r.alertsGenerated : '—') },
  { key: 'fp', header: 'Falso positivo (30 d)', className: 'text-right tabular-nums', render: (r) => (r.falsePositiveRate != null ? formatPercent(r.falsePositiveRate * 100) : '—') },
  { key: 'from', header: 'Vigência desde', render: (r) => formatDate(r.effectiveFrom) },
]

export function RuleListPage() {
  const { can } = useAuth()
  const list = useListState(KEYS, 'code,asc')
  const f = list.filters
  const q = useRules({ status: f.status, category: f.category, severity: f.severity, q: f.q, includeInactive: true }, { page: list.page, size: list.size, sort: list.sort })

  return (
    <>
      <PageHeader
        title="Regras de monitoramento"
        subtitle="Cenários da Carta Circular 4.001, parâmetros e histórico de versões"
        actions={
          <>
            <RunEngineButton />
            {can('rule:write') && (
              <Link to="/regras/nova">
                <Button>Nova regra</Button>
              </Link>
            )}
          </>
        }
      />
      <FilterBar onClear={list.clear} hasActive={list.hasActive}>
        <TextFilter label="Código ou nome" value={f.q} onChange={(v) => list.setFilter('q', v)} />
        <SelectFilter label="Status" value={f.status} onChange={(v) => list.setFilter('status', v)} options={Object.entries(RULE_STATUS_LABEL).map(([value, label]) => ({ value, label }))} />
        <SelectFilter label="Categoria" value={f.category} onChange={(v) => list.setFilter('category', v)} options={Object.entries(CATEGORY_LABEL).map(([value, label]) => ({ value, label }))} />
        <SelectFilter label="Severidade" value={f.severity} onChange={(v) => list.setFilter('severity', v)} options={['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) => ({ value: s, label: severityLabel(s) }))} />
      </FilterBar>
      <DataTable
        caption="Regras de monitoramento"
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={() => void q.refetch()}
        rowKey={(r) => r.id}
        rowClassName={(r) => (r.status === 'PENDING_APPROVAL' ? 'bg-warning-bg/50 hover:bg-warning-bg' : r.status === 'INACTIVE' ? 'text-muted hover:bg-canvas' : 'hover:bg-canvas')}
        sort={list.sort}
        onSort={list.setSort}
        onPage={list.setPage}
        emptyTitle="Nenhuma regra encontrada"
        emptyHint={list.hasActive ? 'Ajuste ou limpe os filtros.' : undefined}
      />
    </>
  )
}
