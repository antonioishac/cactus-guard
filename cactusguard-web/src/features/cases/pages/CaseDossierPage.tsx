import { useParams } from 'react-router'
import { Hourglass } from 'lucide-react'
import type { Case } from '@/api/types'
import { Badge, SeverityBadge, StatusBadge } from '@/components/ui/Badge'
import { SlaBadge } from '@/components/ui/SlaBadge'
import { ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card, KeyValue, PageHeader } from '@/components/ui/Layout'
import { BeneficialOwnersCard, RiskHistoryCard, ScreeningCard } from '@/features/parties/components/PartySections'
import { PartySummaryCard } from '@/features/parties/components/PartySummaryCard'
import { decisionLabel, formatBRL, formatDateTime, severityLabel } from '@/lib/format'
import { CaseActions } from '../components/CaseActions'
import { AlertEvidenceCard, AuditTrailCard, DossierTransactionsCard, EvidencesCard, NotesCard } from '../components/DossierExtras'
import { useCase, useCaseDossier } from '../hooks'
import { currentDeadline, isClosedCase, isPendingApproval } from '../ui-rules'

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
  const cur = currentDeadline(c)
  return (
    <Card title="Prazos regulatórios (Circular BCB 3.978)" actions={<SlaBadge slaStatus={c.slaStatus} deadline={cur.deadline} phase={cur.phase} />}>
      <dl className="grid gap-4 sm:grid-cols-3">
        <KeyValue label="1. Seleção (até 45 dias da ocorrência)">
          {formatDateTime(c.selectionDeadline)}
          {c.selectedAt && <span className="block text-xs text-muted">Selecionado em {formatDateTime(c.selectedAt)}</span>}
        </KeyValue>
        <KeyValue label="2. Análise (até 45 dias da seleção)">
          {formatDateTime(c.analysisDeadline)}
          {c.decidedAt && <span className="block text-xs text-muted">Decidido em {formatDateTime(c.decidedAt)}</span>}
        </KeyValue>
        <KeyValue label="3. Comunicação ao COAF (dia útil seguinte à decisão)">
          {c.communicationDeadline ? formatDateTime(c.communicationDeadline) : <span className="text-xs text-muted">{c.decision === 'COMMUNICATE' ? 'Definido após a aprovação' : 'Não aplicável'}</span>}
        </KeyValue>
      </dl>
    </Card>
  )
}

export function CaseDossierPage() {
  const { id } = useParams()
  const q = useCase(id)
  const dossier = useCaseDossier(id)

  if (q.isLoading) return <Skeleton className="h-64 w-full" />
  if (q.isError) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const c = q.data
  if (!c) return null
  const locked = isClosedCase(c)

  return (
    <>
      <PageHeader title={`Dossiê ${c.caseNumber ?? `#${c.id.slice(0, 8)}`}`} subtitle={`Parte: ${c.partyName ?? '—'}`} />
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <StatusBadge status={c.status} />
        {c.partyRiskLevel && <Badge tone="neutral">Risco da parte: {severityLabel(c.partyRiskLevel)}</Badge>}
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
          {dossier.data && <AlertEvidenceCard dossier={dossier.data} />}
          {dossier.isLoading && <Skeleton className="h-24 w-full" />}
          {dossier.data && <DossierTransactionsCard dossier={dossier.data} />}
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
          {dossier.data && <AuditTrailCard dossier={dossier.data} />}
          {dossier.isError && <ErrorState error={dossier.error} compact />}
        </div>
      </div>
    </>
  )
}
