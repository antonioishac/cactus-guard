import { Link } from 'react-router'
import type { Case, MonitoringRule } from '@/api/types'
import { Badge, SeverityBadge } from '@/components/ui/Badge'
import { DataTable } from '@/components/ui/DataTable'
import type { Column } from '@/components/ui/DataTable'
import { DeadlineBadge } from '@/components/ui/DeadlineBadge'
import { Card, PageHeader } from '@/components/ui/Layout'
import { useAuth } from '@/features/auth/useAuth'
import { useCases } from '@/features/cases/hooks'
import { isOwnDecision } from '@/features/cases/ui-rules'
import { useRules } from '@/features/rules/hooks'
import { isOwnRule } from '@/features/rules/ui-rules'
import { useListState } from '@/hooks/useListState'
import { decisionLabel, formatDateTime } from '@/lib/format'

export function ApprovalsPage() {
  const { user } = useAuth()
  const cases = useListState([] as const, 'analysisDeadline,asc')
  const caseQ = useCases({ status: 'PENDING_APPROVAL' }, { page: cases.page, size: cases.size, sort: cases.sort })
  const ruleQ = useRules({ status: 'PENDING_APPROVAL' }, { page: 0, size: 50, sort: 'code,asc' })

  const caseCols: Column<Case>[] = [
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
    { key: 'prio', header: 'Prioridade', render: (c) => <SeverityBadge severity={String(c.priority)} /> },
    { key: 'decision', header: 'Decisão proposta', render: (c) => (c.decision ? decisionLabel(c.decision) : '—') },
    { key: 'at', header: 'Decidido em', sortField: 'decidedAt', render: (c) => formatDateTime(c.decidedAt) },
    { key: 'deadline', header: 'Prazo de análise', sortField: 'analysisDeadline', render: (c) => <DeadlineBadge stage="analysis" deadline={c.analysisDeadline} /> },
    {
      key: 'eyes',
      header: 'Quatro olhos',
      render: (c) => (isOwnDecision(c, user) ? <Badge tone="warning">Você decidiu: outro supervisor aprova</Badge> : <Link to={`/casos/${c.id}`} className="text-brand underline">Revisar e aprovar</Link>),
    },
  ]
  const ruleCols: Column<MonitoringRule>[] = [
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
    { key: 'sev', header: 'Severidade', render: (r) => <SeverityBadge severity={r.severity} /> },
    {
      key: 'eyes',
      header: 'Quatro olhos',
      render: (r) => (isOwnRule(r, user) ? <Badge tone="warning">Você criou: outro supervisor aprova</Badge> : <Link to={`/regras/${encodeURIComponent(r.code)}`} className="text-brand underline">Revisar e aprovar</Link>),
    },
  ]

  return (
    <>
      <PageHeader title="Fila de aprovação" subtitle="Decisões e alterações que exigem um segundo supervisor (quatro olhos)" />
      <section aria-labelledby="ap-cases" className="mb-6">
        <h2 id="ap-cases" className="mb-2 text-lg font-semibold">Cases aguardando aprovação</h2>
        <DataTable
          caption="Cases aguardando aprovação"
          columns={caseCols}
          page={caseQ.data}
          loading={caseQ.isLoading}
          error={caseQ.error}
          onRetry={() => void caseQ.refetch()}
          rowKey={(c) => c.id}
          sort={cases.sort}
          onSort={cases.setSort}
          onPage={cases.setPage}
          emptyTitle="Nenhum case aguardando aprovação"
        />
      </section>
      <section aria-labelledby="ap-rules" className="mb-6">
        <h2 id="ap-rules" className="mb-2 text-lg font-semibold">Regras aguardando aprovação</h2>
        <DataTable
          caption="Regras aguardando aprovação"
          columns={ruleCols}
          page={ruleQ.data ? { ...ruleQ.data, content: ruleQ.data.content.filter((r) => r.status === 'PENDING_APPROVAL') } : undefined}
          loading={ruleQ.isLoading}
          error={ruleQ.error}
          onRetry={() => void ruleQ.refetch()}
          rowKey={(r) => r.id}
          onPage={() => {}}
          emptyTitle="Nenhuma regra aguardando aprovação"
          emptyHint="A API atual lista apenas regras ativas; pendentes aparecem quando o backend as expuser."
        />
      </section>
      <Card title="Marcações de PEP pendentes">
        <p className="text-sm text-muted">Sem endpoint de listagem de PEP pendentes na API atual. A aprovação é feita na ficha da parte.</p>
      </Card>
    </>
  )
}
