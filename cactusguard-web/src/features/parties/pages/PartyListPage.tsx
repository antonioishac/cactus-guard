import { useState } from 'react'
import { Link } from 'react-router'
import { Plus } from 'lucide-react'
import { PARTY_TYPES, RISK_LEVELS, SOURCE_TYPES } from '@/api/types'
import type { Party, PartyFilters } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { DataTable } from '@/components/ui/DataTable'
import type { Column } from '@/components/ui/DataTable'
import { FilterBar, SelectFilter, TextFilter } from '@/components/ui/Filters'
import { PageHeader } from '@/components/ui/Layout'
import { useAuth } from '@/features/auth/useAuth'
import { useListState } from '@/hooks/useListState'
import { maskDocument } from '@/lib/format'
import { useParties } from '../hooks'
import { isPepActive, RISK_TONE, riskLabel, SOURCE_LABEL } from '../rules'

// Filtros NÃO-PII na URL. Busca por nome/documento fica só em estado local (nunca na URL do navegador).
const KEYS = ['type', 'riskLevel', 'pep', 'sourceType'] as const

const columns: Column<Party>[] = [
  {
    key: 'name',
    header: 'Nome',
    sortField: 'name',
    render: (p) => (
      <Link to={`/partes/${p.id}`} className="font-medium text-brand underline-offset-2 hover:underline">
        {p.name}
      </Link>
    ),
  },
  { key: 'doc', header: 'Documento', render: (p) => <span className="font-mono">{maskDocument(p.document)}</span> },
  { key: 'type', header: 'Tipo', sortField: 'type', render: (p) => (p.type === 'PJ' ? 'PJ' : 'PF') },
  { key: 'risk', header: 'Risco', sortField: 'riskLevel', render: (p) => <Badge tone={RISK_TONE[p.riskLevel ?? ''] ?? 'neutral'}>{riskLabel(p.riskLevel)}</Badge> },
  { key: 'pep', header: 'PEP', render: (p) => (isPepActive(p) ? <Badge tone="danger">PEP</Badge> : <span className="text-muted">—</span>) },
  { key: 'src', header: 'Origem', render: (p) => (p.sourceType ? (SOURCE_LABEL[p.sourceType] ?? p.sourceType) : '—') },
  { key: 'frz', header: 'Bloqueio', render: (p) => (p.assetFreezeActive ? <Badge tone="danger">CSNU</Badge> : <span className="text-muted">—</span>) },
]

export function PartyListPage() {
  const { can } = useAuth()
  const list = useListState(KEYS, 'name,asc')
  const [text, setText] = useState('')
  const [document, setDocument] = useState('')
  const f = list.filters
  const filters: PartyFilters = {
    q: text || undefined,
    document: document || undefined,
    type: f.type || undefined,
    riskLevel: f.riskLevel || undefined,
    pep: f.pep ? f.pep === 'true' : undefined,
    sourceType: f.sourceType || undefined,
  }
  const q = useParties(filters, { page: list.page, size: list.size, sort: list.sort })
  const hasActive = list.hasActive || !!text || !!document

  return (
    <>
      <PageHeader
        title="Partes (KYC/PEP)"
        subtitle="Clientes e relacionados: busca full-text, risco, PEP e ficha 360º"
        actions={can('party:write') ? (
          <Link to="/partes/nova" className="inline-flex items-center gap-2 rounded-lg bg-brand px-3.5 py-2 text-sm font-medium text-white hover:bg-brand-hover">
            <Plus size={16} aria-hidden /> Nova parte
          </Link>
        ) : undefined}
      />
      <FilterBar onClear={() => { setText(''); setDocument(''); list.clear() }} hasActive={hasActive}>
        <TextFilter label="Busca (nome, razão social…)" value={text} onChange={(v) => { setText(v); list.setPage(0) }} placeholder="Buscar" />
        <TextFilter label="Documento (CPF/CNPJ)" value={document} onChange={(v) => { setDocument(v); list.setPage(0) }} placeholder="Somente números" />
        <SelectFilter label="Tipo" value={f.type} onChange={(v) => list.setFilter('type', v)} options={PARTY_TYPES.map((t) => ({ value: t, label: t === 'PF' ? 'Pessoa física' : 'Pessoa jurídica' }))} />
        <SelectFilter label="Risco" value={f.riskLevel} onChange={(v) => list.setFilter('riskLevel', v)} options={RISK_LEVELS.map((r) => ({ value: r, label: riskLabel(r) }))} />
        <SelectFilter label="PEP" value={f.pep} onChange={(v) => list.setFilter('pep', v)} options={[{ value: 'true', label: 'Somente PEP' }, { value: 'false', label: 'Não PEP' }]} />
        <SelectFilter label="Origem do cadastro" value={f.sourceType} onChange={(v) => list.setFilter('sourceType', v)} options={SOURCE_TYPES.map((s) => ({ value: s, label: SOURCE_LABEL[s] ?? s }))} />
      </FilterBar>
      <DataTable
        caption="Lista de partes"
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={() => void q.refetch()}
        rowKey={(p) => p.id}
        sort={list.sort}
        onSort={list.setSort}
        onPage={list.setPage}
        emptyTitle="Nenhuma parte encontrada"
        emptyHint={hasActive ? 'Ajuste ou limpe os filtros.' : 'Cadastre a primeira parte.'}
      />
    </>
  )
}
