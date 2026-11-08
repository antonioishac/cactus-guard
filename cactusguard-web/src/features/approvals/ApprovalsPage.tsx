import { Link } from 'react-router'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { getPendingApprovals } from '@/api/approvals'
import type { PendingCaseItem, PendingPepItem, PendingRuleItem } from '@/api/types'
import { Badge, SeverityBadge } from '@/components/ui/Badge'
import { DataTable } from '@/components/ui/DataTable'
import type { Column } from '@/components/ui/DataTable'
import { ErrorState } from '@/components/ui/Feedback'
import { PageHeader } from '@/components/ui/Layout'
import { SlaBadge } from '@/components/ui/SlaBadge'
import { useListState } from '@/hooks/useListState'
import { decisionLabel, formatDate, formatDateTime } from '@/lib/format'
import { categoryLabel } from '@/features/rules/ui-rules'

const caseCols: Column<PendingCaseItem>[] = [
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
  { key: 'risk', header: 'Risco', render: (c) => (c.partyRiskLevel ? <SeverityBadge severity={c.partyRiskLevel} /> : '—') },
  { key: 'edd', header: 'EDD', render: (c) => (c.edd ? <Badge tone="warning">EDD</Badge> : '—') },
  { key: 'decision', header: 'Decisão proposta', render: (c) => (c.decision ? decisionLabel(c.decision) : '—') },
  { key: 'at', header: 'Decidido em', render: (c) => formatDateTime(c.decidedAt) },
  { key: 'deadline', header: 'Prazo de análise', render: (c) => <SlaBadge slaStatus={c.slaStatus} deadline={c.analysisDeadline} /> },
  {
    key: 'eyes',
    header: 'Quatro olhos',
    render: (c) =>
      c.ownDecision ? (
        <Badge tone="warning">Você decidiu: outro supervisor aprova</Badge>
      ) : (
        <Link to={`/casos/${c.id}`} className="text-brand underline">Revisar e aprovar</Link>
      ),
  },
]

const ruleCols: Column<PendingRuleItem>[] = [
  {
    key: 'rule',
    header: 'Regra',
    render: (r) => (
      <Link to={`/regras/${encodeURIComponent(r.code)}`} className="font-medium text-brand underline-offset-2 hover:underline">
        {r.code} · v{r.version}
        <span className="block text-xs font-normal text-ink">{r.name}</span>
      </Link>
    ),
  },
  { key: 'cat', header: 'Categoria', render: (r) => categoryLabel(r.category) },
  { key: 'sev', header: 'Severidade', render: (r) => <SeverityBadge severity={r.severity} /> },
  { key: 'by', header: 'Criada/alterada por', render: (r) => r.createdByName ?? '—' },
  { key: 'at', header: 'Em', render: (r) => formatDateTime(r.updatedAt) },
  {
    key: 'eyes',
    header: 'Quatro olhos',
    render: (r) =>
      r.ownChange ? (
        <Badge tone="warning">Você alterou: outro supervisor aprova</Badge>
      ) : (
        <Link to={`/regras/${encodeURIComponent(r.code)}`} className="text-brand underline">Revisar e aprovar</Link>
      ),
  },
]

const pepCols: Column<PendingPepItem>[] = [
  {
    key: 'party',
    header: 'Parte',
    render: (p) => (
      <Link to={`/partes/${p.partyId}`} className="font-medium text-brand underline-offset-2 hover:underline">
        {p.partyName}
        <span className="block font-mono text-xs font-normal text-muted">{p.document ?? ''}</span>
      </Link>
    ),
  },
  { key: 'cat', header: 'Categoria', render: (p) => p.pepCategory ?? '—' },
  { key: 'role', header: 'Cargo', render: (p) => p.pepRole ?? '—' },
  { key: 'start', header: 'Início', render: (p) => formatDate(p.pepStartDate) },
  { key: 'risk', header: 'Risco', render: (p) => (p.riskLevel ? <SeverityBadge severity={p.riskLevel} /> : '—') },
  { key: 'at', header: 'Marcada em', render: (p) => formatDateTime(p.markedAt) },
  { key: 'go', header: 'Ação', render: (p) => <Link to={`/partes/${p.partyId}`} className="text-brand underline">Abrir ficha (aba PEP)</Link> },
]

export function ApprovalsPage() {
  // page/size valem para cada uma das três listas (ordem fixa: mais antigos primeiro)
  const list = useListState([] as const, '')
  const q = useQuery({
    queryKey: ['approvals', 'pending', list.page],
    queryFn: () => getPendingApprovals({ page: list.page, size: list.size }),
    placeholderData: keepPreviousData,
  })
  const d = q.data

  return (
    <>
      <PageHeader title="Fila de aprovação" subtitle="Decisões, regras e PEPs que exigem um segundo par de olhos (quatro olhos)" />
      {q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
      <section aria-labelledby="ap-cases" className="mb-6">
        <h2 id="ap-cases" className="mb-2 text-lg font-semibold">Cases aguardando aprovação{d ? ` (${d.counts.cases})` : ''}</h2>
        <DataTable
          caption="Cases aguardando aprovação"
          columns={caseCols}
          page={d?.cases}
          loading={q.isLoading}
          rowKey={(c) => c.id}
          onPage={list.setPage}
          emptyTitle="Nenhum case aguardando aprovação"
        />
      </section>
      <section aria-labelledby="ap-rules" className="mb-6">
        <h2 id="ap-rules" className="mb-2 text-lg font-semibold">Regras aguardando aprovação{d ? ` (${d.counts.rules})` : ''}</h2>
        <DataTable
          caption="Regras aguardando aprovação"
          columns={ruleCols}
          page={d?.rules}
          loading={q.isLoading}
          rowKey={(r) => r.code}
          onPage={list.setPage}
          emptyTitle="Nenhuma regra aguardando aprovação"
        />
      </section>
      <section aria-labelledby="ap-peps" className="mb-6">
        <h2 id="ap-peps" className="mb-2 text-lg font-semibold">PEPs sem aprovação da alta administração{d ? ` (${d.counts.peps})` : ''}</h2>
        <DataTable
          caption="PEPs aguardando aprovação"
          columns={pepCols}
          page={d?.peps}
          loading={q.isLoading}
          rowKey={(p) => p.partyId}
          onPage={list.setPage}
          emptyTitle="Nenhum PEP pendente"
        />
      </section>
    </>
  )
}
