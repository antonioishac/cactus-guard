import { Link } from 'react-router'
import { Zap } from 'lucide-react'
import type { SiscoafCommunication } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { DataTable } from '@/components/ui/DataTable'
import type { Column } from '@/components/ui/DataTable'
import { FilterBar, SelectFilter, TextFilter } from '@/components/ui/Filters'
import { PageHeader } from '@/components/ui/Layout'
import { Button } from '@/components/ui/Button'
import { useAuth } from '@/features/auth/useAuth'
import { useListState } from '@/hooks/useListState'
import { formatBRL, formatDate } from '@/lib/format'
import { STATUS_LABEL, TYPE_LABEL } from '../deadline'
import { SecrecyNotice, SiscoafDeadlineBadge, SiscoafStatusBadge, SiscoafTypeBadge } from '../components/Shared'
import { useSiscoafList } from '../hooks'

const KEYS = ['status', 'type', 'from', 'to', 'caseId'] as const
const TAB_STATUSES = ['DRAFT', 'PENDING_REVIEW', 'APPROVED', 'SUBMITTED', 'ACKNOWLEDGED', 'REJECTED', 'CANCELLED', 'RECTIFIED']
const TYPES = ['COS', 'COE', 'NEGATIVE_DECLARATION', 'COAF_COMMUNICATION']

const columns: Column<SiscoafCommunication>[] = [
  {
    key: 'id',
    header: 'Comunicação',
    render: (c) => (
      <Link to={`/siscoaf/${c.id}`} className="font-medium text-brand underline-offset-2 hover:underline">
        #{c.id.slice(0, 8)}
        {c.caseNumber && <span className="block text-xs font-normal text-muted">{c.caseNumber}</span>}
      </Link>
    ),
  },
  { key: 'party', header: 'Parte', render: (c) => c.partyName ?? '—' },
  {
    key: 'type',
    header: 'Tipo',
    render: (c) => (
      <span className="inline-flex flex-wrap items-center gap-1">
        <SiscoafTypeBadge type={c.type} />
        {c.type === 'COE' && <Badge tone="info">Automática</Badge>}
        {c.originalCommunicationId && <Badge tone="orange">Retificação</Badge>}
      </span>
    ),
  },
  { key: 'status', header: 'Status', sortField: 'status', render: (c) => <SiscoafStatusBadge status={c.status} /> },
  { key: 'amount', header: 'Valor', sortField: 'amount', render: (c) => formatBRL(c.amount) },
  { key: 'year', header: 'Ano ref.', render: (c) => c.referenceYear ?? '—' },
  { key: 'deadline', header: 'Prazo de envio (D+1 útil)', sortField: 'deadlineAt', render: (c) => <SiscoafDeadlineBadge c={c} /> },
  { key: 'protocol', header: 'Protocolo COAF', render: (c) => c.protocolNumber ?? '—' },
  { key: 'created', header: 'Criada em', sortField: 'createdAt', render: (c) => formatDate(c.createdAt) },
]

export function SiscoafListPage() {
  const { can } = useAuth()
  const state = useListState(KEYS, 'deadlineAt,asc')
  // sem `status` o backend lista todos
  const { status, type, from, to, caseId } = state.filters
  const q = useSiscoafList(
    { status: status || undefined, type: type || undefined, from: from || undefined, to: to || undefined, caseId: caseId || undefined },
    { page: state.page, size: state.size, sort: state.sort },
  )
  const page = q.data

  return (
    <>
      <PageHeader
        title="SISCOAF"
        subtitle="Comunicações ao COAF: COS, COE e declaração negativa"
        actions={
          <Link to="/siscoaf/declaracao-negativa">
            <Button variant="secondary">Declaração negativa</Button>
          </Link>
        }
      />
      <SecrecyNotice />
      <div role="note" className="mb-4 flex items-start gap-2 rounded-xl border border-info bg-info-bg p-3 text-sm text-info">
        <Zap size={16} className="mt-0.5 shrink-0" aria-hidden />
        <p>
          <strong>COE automática:</strong> operações em espécie de R$ 50 mil ou mais (inclui provisionamento de saque) são comunicadas por obrigação legal, sem análise de suspeita. Só o prazo de envio deve ser acompanhado.
        </p>
      </div>

      <div role="tablist" aria-label="Status das comunicações" className="mb-3 flex flex-wrap gap-1">
        <button
          role="tab"
          aria-selected={!status}
          onClick={() => state.setFilter('status', '')}
          className={`rounded-lg px-3 py-1.5 text-sm ${!status ? 'bg-brand text-white' : 'border border-line bg-surface hover:bg-neutral-bg'}`}
        >
          Todas
        </button>
        {TAB_STATUSES.map((s) => (
          <button
            key={s}
            role="tab"
            aria-selected={status === s}
            onClick={() => state.setFilter('status', s)}
            className={`rounded-lg px-3 py-1.5 text-sm ${status === s ? 'bg-brand text-white' : 'border border-line bg-surface hover:bg-neutral-bg'}`}
          >
            {STATUS_LABEL[s]}
          </button>
        ))}
      </div>
      <FilterBar onClear={state.clear} hasActive={state.hasActive}>
        <SelectFilter label="Tipo" value={type} onChange={(v) => state.setFilter('type', v)} options={TYPES.map((t) => ({ value: t, label: TYPE_LABEL[t] ?? t }))} />
        <TextFilter label="Criada de" type="date" value={from} onChange={(v) => state.setFilter('from', v)} />
        <TextFilter label="Criada até" type="date" value={to} onChange={(v) => state.setFilter('to', v)} />
        <TextFilter label="ID do case" value={caseId} onChange={(v) => state.setFilter('caseId', v.trim())} placeholder="UUID" />
      </FilterBar>
      <DataTable
        caption="Comunicações SISCOAF"
        columns={columns}
        page={page}
        loading={q.isLoading}
        error={q.error}
        onRetry={() => void q.refetch()}
        rowKey={(c) => c.id}
        rowClassName={(c) => (c.status === 'REJECTED' ? 'bg-danger-bg/60 hover:bg-danger-bg' : ['DRAFT', 'PENDING_REVIEW'].includes(c.status) ? 'bg-warning-bg/40 hover:bg-warning-bg' : 'hover:bg-canvas')}
        sort={state.sort}
        onSort={state.setSort}
        onPage={state.setPage}
        emptyTitle="Nenhuma comunicação encontrada"
        emptyHint={can('siscoaf:act') ? 'Rascunhos nascem de cases com decisão COMUNICAR aprovada.' : undefined}
      />
    </>
  )
}
