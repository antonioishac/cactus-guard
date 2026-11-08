import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { useQueries } from '@tanstack/react-query'
import { ArrowUpRight, FolderPlus, Hand, ShieldOff } from 'lucide-react'
import { getTransaction } from '@/api/parties'
import type { AlertDetail } from '@/api/types'
import { Badge, SeverityBadge, StatusBadge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { DeadlineBadge } from '@/components/ui/DeadlineBadge'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback'
import { JustificationDialog } from '@/components/ui/JustificationDialog'
import { Card, KeyValue, PageHeader } from '@/components/ui/Layout'
import { useAuth } from '@/features/auth/useAuth'
import { PartySummaryCard } from '@/features/parties/components/PartySummaryCard'
import { TransactionsCard } from '@/features/parties/components/PartySections'
import { formatBRL, formatDateTime, maskDocument } from '@/lib/format'
import { useAlert, useAssumeAlert, useEscalateAlert, useFalsePositive, useOpenCase } from '../hooks'

type Dialog = 'escalate' | 'falsePositive' | 'openCase' | null

const ACTIVE = ['OPEN', 'IN_ANALYSIS', 'ESCALATED']

function EvidenceCard({ a }: { a: AlertDetail }) {
  return (
    <Card title="Evidências: parâmetros e valores observados">
      {!a.evidence || a.evidence.length === 0 ? (
        <EmptyState title="Evidências não disponíveis" hint="O backend ainda não expõe o snapshot de evidências deste alerta." />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase text-muted">
              <tr>
                <th scope="col" className="px-2 py-1">Parâmetro</th>
                <th scope="col" className="px-2 py-1">Limite da regra</th>
                <th scope="col" className="px-2 py-1">Valor observado</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {a.evidence.map((e) => (
                <tr key={e.parameter}>
                  <td className="px-2 py-1.5">{e.label ?? e.parameter}</td>
                  <td className="px-2 py-1.5">{e.threshold ?? '—'}</td>
                  <td className="px-2 py-1.5 font-medium">{e.observed ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}

function LinkedTransactions({ a }: { a: AlertDetail }) {
  const ids = a.triggeringTransactionIds ?? []
  const results = useQueries({ queries: ids.map((id) => ({ queryKey: ['transaction', id], queryFn: () => getTransaction(id) })) })
  if (ids.length === 0) return <TransactionsCard partyId={a.partyId} title="Transações da parte (vínculo direto não disponível)" />
  return (
    <Card title="Transações que dispararam o alerta">
      <ul className="divide-y divide-line text-sm">
        {results.map((r, i) => (
          <li key={ids[i]} className="flex flex-wrap justify-between gap-2 py-2">
            {r.isLoading && <Skeleton className="h-4 w-full" />}
            {r.data && (
              <>
                <span>{formatDateTime(r.data.transactionDate)} · {r.data.type}{r.data.cash && ' (espécie)'}</span>
                <span className="tabular-nums">{formatBRL(r.data.amountBrl ?? r.data.amount)}</span>
                <span className="text-muted">{r.data.counterpartyName ?? '—'} {maskDocument(r.data.counterpartyDocument)}</span>
              </>
            )}
            {r.isError && <ErrorState error={r.error} compact />}
          </li>
        ))}
      </ul>
    </Card>
  )
}

export function AlertDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { can, user } = useAuth()
  const q = useAlert(id)
  const [dialog, setDialog] = useState<Dialog>(null)
  const assume = useAssumeAlert()
  const escalate = useEscalateAlert()
  const falsePositive = useFalsePositive()
  const openCase = useOpenCase()

  if (q.isLoading) return <Skeleton className="h-64 w-full" />
  if (q.isError) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const a = q.data
  if (!a) return null

  const active = ACTIVE.includes(a.status)
  const mine = !!user && a.assignedToId === user.id

  return (
    <>
      <PageHeader
        title={a.title ?? `Alerta ${a.type}`}
        subtitle={`Parte: ${a.partyName ?? '—'}`}
        actions={
          <>
            {can('alert:assume') && (
              <Button variant="secondary" disabled={!active || mine} loading={assume.isPending} onClick={() => assume.mutate(a.id)}>
                <Hand size={16} aria-hidden /> Assumir
              </Button>
            )}
            {can('alert:escalate') && (
              <Button variant="secondary" disabled={!active} onClick={() => setDialog('escalate')}>
                <ArrowUpRight size={16} aria-hidden /> Escalar
              </Button>
            )}
            {can('alert:falsePositive') && (
              <Button variant="secondary" disabled={!active} onClick={() => setDialog('falsePositive')}>
                <ShieldOff size={16} aria-hidden /> Falso positivo
              </Button>
            )}
            {can('case:open') && (
              <Button disabled={!active} onClick={() => setDialog('openCase')}>
                <FolderPlus size={16} aria-hidden /> Abrir case
              </Button>
            )}
          </>
        }
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <Card title="Resumo">
            <div className="mb-3 flex flex-wrap gap-2">
              <SeverityBadge severity={a.severity} />
              <StatusBadge status={a.status} />
              {a.cc4001Indicator && <Badge tone="info">Carta 4.001: {a.cc4001Indicator}</Badge>}
            </div>
            {a.description && <p className="mb-3 text-sm">{a.description}</p>}
            <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              <KeyValue label="Regra">{a.ruleCode ?? '—'}</KeyValue>
              <KeyValue label="Versão da regra">{a.ruleVersion ? `v${a.ruleVersion}` : '—'}</KeyValue>
              <KeyValue label="Tipo">{a.type}</KeyValue>
              <KeyValue label="Ocorrência (fato gerador)">{formatDateTime(a.occurredAt)}</KeyValue>
              <KeyValue label="Valor total">{formatBRL(a.totalAmount)}</KeyValue>
              <KeyValue label="Prazo de seleção (45 dias)">
                <DeadlineBadge stage="selection" deadline={a.selectionDeadline} />
              </KeyValue>
              {a.falsePositiveReason && <KeyValue label="Motivo do falso positivo">{a.falsePositiveReason}</KeyValue>}
            </dl>
          </Card>

          <EvidenceCard a={a} />
          <LinkedTransactions a={a} />

          <Card title="Histórico do alerta">
            {!a.history || a.history.length === 0 ? (
              <EmptyState title="Sem histórico disponível" />
            ) : (
              <ol className="space-y-2 text-sm">
                {a.history.map((h) => (
                  <li key={h.id} className="border-l-2 border-line pl-3">
                    <p><span className="font-medium">{h.action}</span> · {h.actorName ?? 'sistema'} · {formatDateTime(h.createdAt)}</p>
                    {h.justification && <p className="text-muted">{h.justification}</p>}
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </div>

        <div>
          <PartySummaryCard partyId={a.partyId} />
        </div>
      </div>

      <JustificationDialog
        open={dialog === 'escalate'}
        title="Escalar alerta"
        description="O alerta será encaminhado ao nível superior. Escalar não encerra o alerta."
        confirmLabel="Escalar"
        onCancel={() => setDialog(null)}
        onConfirm={async ({ justification }) => {
          await escalate.mutateAsync({ id: a.id, justification })
          setDialog(null)
        }}
      />
      <JustificationDialog
        open={dialog === 'falsePositive'}
        title="Marcar como falso positivo"
        description="Ação irreversível: o alerta será encerrado e o motivo ficará na trilha de auditoria."
        confirmLabel="Marcar falso positivo"
        reasonLabel="Motivo (resumo, até 100 caracteres)"
        tone="danger"
        onCancel={() => setDialog(null)}
        onConfirm={async ({ justification, reason }) => {
          await falsePositive.mutateAsync({ id: a.id, reason: reason ?? '', justification })
          setDialog(null)
        }}
      />
      <ConfirmDialog
        open={dialog === 'openCase'}
        title="Abrir case a partir do alerta"
        description="O alerta será selecionado para análise e o prazo regulatório de 45 dias para a análise será iniciado."
        confirmLabel="Abrir case"
        onCancel={() => setDialog(null)}
        onConfirm={async () => {
          const c = await openCase.mutateAsync(a.id)
          setDialog(null)
          void navigate(`/casos/${c.id}`)
        }}
      />
    </>
  )
}
