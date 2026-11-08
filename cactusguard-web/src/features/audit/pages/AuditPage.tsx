import { useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import type { AuditEntry, DataAccessEntry, Page } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { DataTable } from '@/components/ui/DataTable'
import type { Column } from '@/components/ui/DataTable'
import { FilterBar, SelectFilter, TextFilter } from '@/components/ui/Filters'
import { Card, PageHeader } from '@/components/ui/Layout'
import { useListState } from '@/hooks/useListState'
import { formatDateTime } from '@/lib/format'
import { AuditDiff } from '../components/AuditDiff'
import { useAuditByEntity, useDataAccessByEntity, useDataAccessByUser } from '../hooks'

const KEYS = ['tab', 'entityType', 'entityId', 'user', 'action', 'from', 'to', 'userId'] as const
const ENTITY_TYPES = ['CASE', 'ALERT', 'PARTY', 'SISCOAF_COMMUNICATION', 'MONITORING_RULE', 'REPORT_JOB', 'USER', 'KYC_DOCUMENT']
const UUID_RE = /^[A-Za-z0-9-]{4,36}$/

const who = (u: { name?: string; username?: string; id: string } | null | undefined) => u?.name ?? u?.username ?? (u ? u.id.slice(0, 8) : 'Sistema')

/** Filtros que o backend não oferece são aplicados sobre a página recebida. */
function clientFilter<T extends { user?: { id: string; name?: string; username?: string } | null; createdAt: string; action?: string }>(
  page: Page<T> | undefined,
  f: { user: string; action: string; from: string; to: string },
): Page<T> | undefined {
  if (!page) return page
  const user = f.user.trim().toLowerCase()
  const action = f.action.trim().toLowerCase()
  const content = page.content.filter((e) => {
    const day = e.createdAt.slice(0, 10)
    return (
      (!user || who(e.user).toLowerCase().includes(user) || e.user?.id === f.user) &&
      (!action || (e.action ?? '').toLowerCase().includes(action)) &&
      (!f.from || day >= f.from) &&
      (!f.to || day <= f.to)
    )
  })
  return { ...page, content }
}

function TrailRow({ e }: { e: AuditEntry }) {
  const [open, setOpen] = useState(false)
  return (
    <div>
      <button onClick={() => setOpen((o) => !o)} aria-expanded={open} className="inline-flex items-center gap-1 text-brand underline-offset-2 hover:underline">
        {open ? <ChevronDown size={14} aria-hidden /> : <ChevronRight size={14} aria-hidden />}
        {open ? 'Ocultar alterações' : 'Ver alterações'}
      </button>
      {open && (
        <div className="mt-2 rounded-lg border border-line bg-canvas p-2">
          <AuditDiff oldValue={e.oldValue} newValue={e.newValue} />
        </div>
      )}
    </div>
  )
}

const trailColumns: Column<AuditEntry>[] = [
  { key: 'when', header: 'Data', sortField: 'createdAt', render: (e) => formatDateTime(e.createdAt) },
  { key: 'action', header: 'Ação', render: (e) => <Badge tone="info">{e.action}</Badge> },
  { key: 'entity', header: 'Entidade', render: (e) => <span className="text-xs">{e.entityType}<br />{e.entityId?.slice(0, 8)}</span> },
  { key: 'user', header: 'Usuário', render: (e) => who(e.user) },
  { key: 'ip', header: 'IP', render: (e) => e.ipAddress ?? '—' },
  { key: 'diff', header: 'Alterações', render: (e) => <TrailRow e={e} /> },
]

const accessColumns: Column<DataAccessEntry>[] = [
  { key: 'when', header: 'Data', sortField: 'createdAt', render: (e) => formatDateTime(e.createdAt) },
  { key: 'user', header: 'Usuário', render: (e) => who(e.user) },
  { key: 'entity', header: 'Entidade', render: (e) => <span className="text-xs">{e.entityType}<br />{e.entityId.slice(0, 8)}</span> },
  { key: 'fields', header: 'Campos acessados', render: (e) => e.fieldsAccessed ?? '—' },
  { key: 'reason', header: 'Motivo', render: (e) => e.accessReason ?? '—' },
  { key: 'ip', header: 'IP', render: (e) => e.ipAddress ?? '—' },
]

const TABS = [
  { id: 'trail', label: 'Trilha de auditoria' },
  { id: 'access', label: 'Acesso a dados pessoais (LGPD)' },
] as const

export function AuditPage() {
  const list = useListState(KEYS, 'createdAt,desc')
  const f = list.filters
  const tab = f.tab === 'access' ? 'access' : 'trail'
  const paging = { page: list.page, size: list.size, sort: list.sort }
  const entityOk = !!f.entityType && UUID_RE.test(f.entityId)
  const userOk = UUID_RE.test(f.userId)

  const trail = useAuditByEntity(tab === 'trail' && entityOk ? f.entityType : '', f.entityId, paging)
  const accessEntity = useDataAccessByEntity(tab === 'access' && !userOk && entityOk ? f.entityType : '', f.entityId, paging)
  const accessUser = useDataAccessByUser(tab === 'access' && userOk ? f.userId : '', paging)
  const access = userOk ? accessUser : accessEntity

  const cf = { user: f.user, action: f.action, from: f.from, to: f.to }

  return (
    <>
      <PageHeader title="Auditoria" subtitle="Trilha imutável de ações e registro de acesso a dados pessoais (somente leitura)" />
      <div role="tablist" aria-label="Seções" className="mb-3 flex gap-1">
        {TABS.map((t) => (
          <button
            key={t.id}
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => list.setFilter('tab', t.id === 'trail' ? '' : t.id)}
            className={`rounded-lg px-3 py-1.5 text-sm ${tab === t.id ? 'bg-brand text-white' : 'border border-line bg-surface hover:bg-neutral-bg'}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <FilterBar onClear={() => list.clear()} hasActive={list.hasActive}>
        <SelectFilter label="Tipo de entidade" value={f.entityType} onChange={(v) => list.setFilter('entityType', v)} options={ENTITY_TYPES.map((t) => ({ value: t, label: t }))} />
        <TextFilter label="ID da entidade" value={f.entityId} onChange={(v) => list.setFilter('entityId', v.trim())} placeholder="UUID" />
        {tab === 'trail' ? (
          <>
            <TextFilter label="Usuário (nome)" value={f.user} onChange={(v) => list.setFilter('user', v)} />
            <TextFilter label="Ação" value={f.action} onChange={(v) => list.setFilter('action', v)} placeholder="ex.: SISCOAF_SUBMIT" />
            <TextFilter label="De" type="date" value={f.from} onChange={(v) => list.setFilter('from', v)} />
            <TextFilter label="Até" type="date" value={f.to} onChange={(v) => list.setFilter('to', v)} />
          </>
        ) : (
          <TextFilter label="ID do usuário (alternativa à entidade)" value={f.userId} onChange={(v) => list.setFilter('userId', v.trim())} placeholder="UUID" />
        )}
      </FilterBar>

      <p role="note" className="mb-3 text-xs text-muted">
        {tab === 'trail'
          ? 'A API consulta a trilha por entidade: informe tipo e ID. Usuário, ação e período filtram os registros da página exibida.'
          : 'Consulte por entidade (tipo e ID) ou por usuário (ID).'}
      </p>

      {tab === 'trail' && !entityOk && <Card><p className="text-sm text-muted">Informe o tipo e o ID da entidade para consultar a trilha.</p></Card>}
      {tab === 'access' && !entityOk && !userOk && <Card><p className="text-sm text-muted">Informe a entidade ou o usuário para consultar os acessos.</p></Card>}

      {tab === 'trail' && entityOk && (
        <DataTable
          caption="Trilha de auditoria"
          columns={trailColumns}
          page={clientFilter(trail.data, cf)}
          loading={trail.isLoading}
          error={trail.error}
          onRetry={() => void trail.refetch()}
          rowKey={(e) => e.id}
          sort={list.sort}
          onSort={list.setSort}
          onPage={list.setPage}
          emptyTitle="Nenhum registro encontrado"
        />
      )}
      {tab === 'access' && (entityOk || userOk) && (
        <DataTable
          caption="Acessos a dados pessoais"
          columns={accessColumns}
          page={access.data}
          loading={access.isLoading}
          error={access.error}
          onRetry={() => void access.refetch()}
          rowKey={(e) => e.id}
          sort={list.sort}
          onSort={list.setSort}
          onPage={list.setPage}
          emptyTitle="Nenhum acesso registrado"
        />
      )}
    </>
  )
}
