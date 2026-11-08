import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ArrowUpRight, FolderPlus, Hand, Send, ShieldOff } from 'lucide-react'
import type { AlertDetail } from '@/api/types'
import { Badge, SeverityBadge, StatusBadge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { DeadlineBadge } from '@/components/ui/DeadlineBadge'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback'
import { JustificationDialog } from '@/components/ui/JustificationDialog'
import { Card, KeyValue, PageHeader } from '@/components/ui/Layout'
import { useAuth } from '@/features/auth/useAuth'
import { PartySummaryCard } from '@/features/parties/components/PartySummaryCard'
import { directionLabel } from '@/features/parties/rules'
import { formatBRL, formatDateTime } from '@/lib/format'
import { useAddAlertComment, useAlert, useAlertComments, useAssumeAlert, useEscalateAlert, useFalsePositive, useOpenCase } from '../hooks'

type Dialog = 'escalate' | 'falsePositive' | 'openCase' | null

const ACTIVE = ['OPEN', 'IN_ANALYSIS', 'ESCALATED']

function EvidenceCard({ a }: { a: AlertDetail }) {
  return (
    <Card title="Evidências: parâmetros e valores observados">
      {!a.evidence || a.evidence.length === 0 ? (
        <EmptyState title="Sem evidências registradas" hint="O alerta não guardou snapshot de evidências do disparo." />
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
  const txs = a.transactions ?? []
  return (
    <Card title="Transações que dispararam o alerta">
      {txs.length === 0 ? (
        <EmptyState title="Nenhuma transação vinculada" />
      ) : (
        <ul className="divide-y divide-line text-sm">
          {txs.map((t) => (
            <li key={t.id} className="flex flex-wrap justify-between gap-2 py-2">
              <span>
                {formatDateTime(t.transactionDate)} · {t.paymentMethod ?? t.type}
                {t.cash && ' (espécie)'} · {directionLabel(t.direction)}
              </span>
              <span className="tabular-nums">{formatBRL(t.amount)}</span>
              <span className="text-muted">{t.counterpartyName ?? '—'} {t.counterpartyDocument ?? ''}</span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function CommentsCard({ alertId, canWrite }: { alertId: string; canWrite: boolean }) {
  const q = useAlertComments(alertId)
  const add = useAddAlertComment(alertId)
  const [text, setText] = useState('')
  return (
    <Card title="Comentários">
      {q.isLoading && <Skeleton className="h-12 w-full" />}
      {q.isError && <ErrorState error={q.error} compact />}
      {q.data && q.data.length === 0 && <EmptyState title="Nenhum comentário" />}
      {q.data && q.data.length > 0 && (
        <ul className="mb-3 space-y-2 text-sm">
          {q.data.map((c) => (
            <li key={c.id} className="rounded-lg bg-canvas p-2">
              <p>{c.text}</p>
              <p className="text-xs text-muted">{c.authorName} · {formatDateTime(c.createdAt)}</p>
            </li>
          ))}
        </ul>
      )}
      {canWrite && (
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            if (!text.trim()) return
            add.mutate(text.trim(), { onSuccess: () => setText('') })
          }}
        >
          <label className="sr-only" htmlFor={`comment-${alertId}`}>Novo comentário</label>
          <input id={`comment-${alertId}`} value={text} maxLength={5000} onChange={(e) => setText(e.target.value)} placeholder="Escreva um comentário (não muda o status)" className="flex-1 rounded-lg border border-line px-3 py-2 text-sm" />
          <Button type="submit" loading={add.isPending} disabled={!text.trim()}>
            <Send size={14} aria-hidden /> Comentar
          </Button>
        </form>
      )}
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
              {a.rule?.carta4001Reference && <Badge tone="info">Carta 4.001: {a.rule.carta4001Reference}</Badge>}
            </div>
            {a.description && <p className="mb-3 text-sm">{a.description}</p>}
            <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              <KeyValue label="Regra">{a.rule ? `${a.rule.code} · ${a.rule.name}` : (a.ruleCode ?? '—')}</KeyValue>
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
          <CommentsCard alertId={a.id} canWrite={can('alert:assume')} />

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
      <JustificationDialog
        open={dialog === 'openCase'}
        title="Abrir case a partir do alerta"
        description="O alerta será selecionado para análise e o prazo regulatório de 45 dias para a análise será iniciado. A justificativa da seleção fica na trilha de auditoria."
        confirmLabel="Abrir case"
        onCancel={() => setDialog(null)}
        onConfirm={async ({ justification }) => {
          const c = await openCase.mutateAsync({ alertId: a.id, justification })
          setDialog(null)
          void navigate(`/casos/${c.id}`)
        }}
      />
    </>
  )
}
