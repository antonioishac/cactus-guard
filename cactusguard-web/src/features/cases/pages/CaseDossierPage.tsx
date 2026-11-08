import { useParams } from 'react-router'
import { Hourglass } from 'lucide-react'
import type { Case } from '@/api/types'
import { Badge, SeverityBadge, StatusBadge } from '@/components/ui/Badge'
import { DeadlineBadge } from '@/components/ui/DeadlineBadge'
import { ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card, KeyValue, PageHeader } from '@/components/ui/Layout'
import { useAlert } from '@/features/alerts/hooks'
import { BeneficialOwnersCard, RiskHistoryCard, ScreeningCard, TransactionsCard } from '@/features/parties/components/PartySections'
import { PartySummaryCard } from '@/features/parties/components/PartySummaryCard'
import { decisionLabel, formatBRL, formatDateTime } from '@/lib/format'
import { CaseActions } from '../components/CaseActions'
import { AuditTrailCard, EvidencesCard, NotesCard } from '../components/DossierExtras'
import { useCase } from '../hooks'
import { isFinalCase, isPendingApproval } from '../ui-rules'

function PendingApprovalBanner({ c }: { c: Case }) {
  return (
    <div role="status" className="mb-4 flex items-start gap-3 rounded-xl border border-warning bg-warning-bg p-4 text-warning">
      <Hourglass size={20} className="mt-0.5 shrink-0" aria-hidden />
      <div>
        <p className="font-semibold">Aguardando aprovação (quatro olhos)</p>
        <p className="text-sm">
          Decisão proposta: <strong>{c.decision ? decisionLabel(c.decision) : '—'}</strong>
          {c.decidedAt && <> em {formatDateTime(c.decidedAt)}</>}. Um supervisor diferente de quem decidiu precisa aprovar. O prazo de análise continua correndo.
        </p>
        {c.decisionJustification && <p className="mt-1 text-sm">Justificativa: {c.decisionJustification}</p>}
      </div>
    </div>
  )
}

function DeadlinesCard({ c }: { c: Case }) {
  // Seleção: prazo vem do alerta de origem (occurredAt + 45 d); cumprida quando o case foi aberto (selectedAt)
  const alert = useAlert(c.alertId)
  const decided = !!c.decidedAt
  return (
    <Card title="Prazos regulatórios (Circular BCB 3.978)">
      <dl className="grid gap-4 sm:grid-cols-3">
        <KeyValue label="1. Seleção (até 45 dias da ocorrência)">
          {alert.isLoading ? <Skeleton className="h-8 w-32" /> : <DeadlineBadge stage="selection" deadline={alert.data?.selectionDeadline} doneAt={c.selectedAt} />}
        </KeyValue>
        <KeyValue label="2. Análise (até 45 dias da seleção)">
          <DeadlineBadge stage="analysis" deadline={c.analysisDeadline} doneAt={decided ? c.decidedAt : null} />
        </KeyValue>
        <KeyValue label="3. Comunicação ao COAF (dia útil seguinte à decisão)">
          {c.communicationDeadline ? (
            <DeadlineBadge stage="communication" deadline={c.communicationDeadline} />
          ) : (
            <span className="text-xs text-muted">{c.decision === 'COMMUNICATE' ? 'Definido após a aprovação' : 'Não aplicável'}</span>
          )}
        </KeyValue>
      </dl>
    </Card>
  )
}

export function CaseDossierPage() {
  const { id } = useParams()
  const q = useCase(id)

  if (q.isLoading) return <Skeleton className="h-64 w-full" />
  if (q.isError) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const c = q.data
  if (!c) return null
  const locked = isFinalCase(c)

  return (
    <>
      <PageHeader title={`Dossiê ${c.caseNumber ?? `#${c.id.slice(0, 8)}`}`} subtitle={`Parte: ${c.partyName ?? '—'}`} />
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <StatusBadge status={c.status} />
        <SeverityBadge severity={String(c.priority)} />
        {c.edd && <Badge tone="warning">EDD</Badge>}
        {c.escalationLevel > 0 && <Badge tone="orange">Nível de escalonamento {c.escalationLevel}</Badge>}
        {c.indicatorCodes?.map((code) => <Badge key={code} tone="info">Carta 4.001: {code}</Badge>)}
      </div>

      {isPendingApproval(c) && <PendingApprovalBanner c={c} />}
      <div className="mb-4">
        <CaseActions c={c} />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <DeadlinesCard c={c} />
          <PartySummaryCard partyId={c.partyId} />
          <BeneficialOwnersCard partyId={c.partyId} />
          <RiskHistoryCard partyId={c.partyId} />
          <ScreeningCard partyId={c.partyId} />
          <TransactionsCard partyId={c.partyId} />
        </div>
        <div className="space-y-4">
          <Card title="Decisão">
            {c.decision ? (
              <dl className="space-y-2">
                <KeyValue label="Decisão">{decisionLabel(c.decision)}</KeyValue>
                <KeyValue label="Em">{formatDateTime(c.decidedAt)}</KeyValue>
                <KeyValue label="Justificativa">{c.decisionJustification ?? '—'}</KeyValue>
                <KeyValue label="Aprovação">{c.approvedAt ? `Aprovada em ${formatDateTime(c.approvedAt)}` : 'Pendente / não exigida'}</KeyValue>
              </dl>
            ) : (
              <p className="text-sm text-muted">Nenhuma decisão registrada.</p>
            )}
            <dl className="mt-3 grid grid-cols-2 gap-2 border-t border-line pt-3">
              <KeyValue label="Valor envolvido">{formatBRL(c.totalAmountInvolved)}</KeyValue>
              <KeyValue label="Aberto em">{formatDateTime(c.selectedAt ?? c.createdAt)}</KeyValue>
            </dl>
          </Card>
          <EvidencesCard caseId={c.id} locked={locked} />
          <NotesCard caseId={c.id} locked={locked} />
          <AuditTrailCard caseId={c.id} />
        </div>
      </div>
    </>
  )
}
