import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Zap } from 'lucide-react'
import type { SiscoafCommunication } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ErrorState, Skeleton } from '@/components/ui/Feedback'
import { JustificationDialog } from '@/components/ui/JustificationDialog'
import { Card, KeyValue, PageHeader } from '@/components/ui/Layout'
import { useAuth } from '@/features/auth/useAuth'
import { formatBRL, formatDate, formatDateTime } from '@/lib/format'
import { PayloadView } from '../components/PayloadView'
import { SecrecyNotice, SiscoafDeadlineBadge, SiscoafStatusBadge, SiscoafTypeBadge } from '../components/Shared'
import { buildTimeline, isAutomaticCoe, isApprovable, isCancellable, isRectifiable, isSendable, siscoafStatusLabel } from '../deadline'
import { useApproveSiscoaf, useCancelSiscoaf, useRectifySiscoaf, useSiscoaf, useSubmitSiscoaf } from '../hooks'

type Dialog = 'approve' | 'submit' | 'cancel' | 'rectify' | null

function Actions({ c }: { c: SiscoafCommunication }) {
  const { can } = useAuth()
  const navigate = useNavigate()
  const [dialog, setDialog] = useState<Dialog>(null)
  const approve = useApproveSiscoaf()
  const submit = useSubmitSiscoaf()
  const cancel = useCancelSiscoaf()
  const rectify = useRectifySiscoaf()

  // AUDITOR/ADMIN/ANALYST: somente leitura
  if (!can('siscoaf:act')) {
    return <p className="text-sm text-muted">Seu perfil tem acesso somente leitura a esta comunicação.</p>
  }
  const close = () => setDialog(null)
  const buttons = [
    isApprovable(c) && <Button key="a" onClick={() => setDialog('approve')}>Aprovar</Button>,
    isSendable(c) && <Button key="s" onClick={() => setDialog('submit')}>Enviar ao COAF</Button>,
    isCancellable(c) && <Button key="c" variant="danger" onClick={() => setDialog('cancel')}>Cancelar</Button>,
    isRectifiable(c) && <Button key="r" variant="secondary" onClick={() => setDialog('rectify')}>Retificar</Button>,
  ].filter(Boolean)

  return (
    <>
      {buttons.length > 0 ? <div className="flex flex-wrap gap-2">{buttons}</div> : <p className="text-sm text-muted">Nenhuma ação disponível no status {siscoafStatusLabel(c.status)}.</p>}

      <JustificationDialog
        open={dialog === 'approve'}
        title="Aprovar comunicação"
        description="A aprovação libera o envio ao COAF. Registre o fundamento da aprovação."
        confirmLabel="Aprovar"
        onCancel={close}
        onConfirm={async ({ justification }) => {
          await approve.mutateAsync({ id: c.id, justification })
          close()
        }}
      />
      <JustificationDialog
        open={dialog === 'submit'}
        title="Enviar ao COAF"
        description="Ação regulatória irreversível: a comunicação será transmitida ao COAF e ficará registrada na trilha de auditoria. Não dê ciência ao cliente."
        confirmLabel="Enviar ao COAF"
        onCancel={close}
        onConfirm={async ({ justification }) => {
          await submit.mutateAsync({ id: c.id, justification })
          close()
        }}
      />
      <JustificationDialog
        open={dialog === 'cancel'}
        title="Cancelar comunicação"
        description="Informe o motivo do cancelamento (obrigatório). Comunicações já enviadas não podem ser canceladas, apenas retificadas."
        confirmLabel="Cancelar comunicação"
        tone="danger"
        onCancel={close}
        onConfirm={async ({ justification }) => {
          await cancel.mutateAsync({ id: c.id, justification })
          close()
        }}
      />
      <JustificationDialog
        open={dialog === 'rectify'}
        title="Retificar comunicação enviada"
        description="A original será marcada como retificada e um novo rascunho vinculado será criado."
        confirmLabel="Criar retificação"
        onCancel={close}
        onConfirm={async ({ justification }) => {
          const created = await rectify.mutateAsync({ id: c.id, justification })
          close()
          void navigate(`/siscoaf/${created.id}`)
        }}
      />
    </>
  )
}

export function SiscoafDetailPage() {
  const { id } = useParams()
  const q = useSiscoaf(id)

  if (q.isLoading) return <Skeleton className="h-64 w-full" />
  if (q.error || !q.data) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const c = q.data
  const indicators = c.indicatorCodes ?? (Array.isArray(c.payload?.indicators) ? (c.payload.indicators as string[]) : [])

  return (
    <>
      <PageHeader
        title={`Comunicação #${c.id.slice(0, 8)}`}
        subtitle="Detalhe da comunicação ao COAF"
        actions={
          <span className="flex items-center gap-2">
            <SiscoafTypeBadge type={c.type} />
            <SiscoafStatusBadge status={c.status} />
          </span>
        }
      />
      <SecrecyNotice />
      {c.fourEyesRequired && (
        <div role="note" className="mb-4 flex items-start gap-3 rounded-xl border border-warning bg-warning-bg p-3 text-sm text-warning">
          <p>
            <strong>Quatro olhos exigido</strong> (PEP, risco alto/crítico ou EDD): o aprovador deve ser diferente de quem decidiu o case e de quem criou o rascunho, e o envio exige aprovação prévia.
            {c.approvedById ? ` Aprovada em ${formatDateTime(c.approvedAt)}.` : ' Ainda não aprovada.'}
          </p>
        </div>
      )}
      {isAutomaticCoe(c) && (
        <div role="note" className="mb-4 flex items-start gap-3 rounded-xl border border-info bg-info-bg p-3 text-sm text-info">
          <Zap size={18} className="mt-0.5 shrink-0" aria-hidden />
          <p>
            <strong>COE automática (espécie igual ou superior a R$ 50 mil).</strong> Fluxo legal: dispensa análise de suspeita; o envio deve ocorrer no prazo.
          </p>
        </div>
      )}
      {c.originalCommunicationId && (
        <p className="mb-4 text-sm">
          Retificação da comunicação{' '}
          <Link className="text-brand underline" to={`/siscoaf/${c.originalCommunicationId}`}>
            #{c.originalCommunicationId.slice(0, 8)}
          </Link>
          .
        </p>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        <Card title="Dados" className="lg:col-span-2">
          <dl className="grid gap-4 sm:grid-cols-3">
            <KeyValue label="Valor">{formatBRL(c.amount)}</KeyValue>
            <KeyValue label="Ano de referência">{c.referenceYear ?? '—'}</KeyValue>
            <KeyValue label="Período">{c.periodFrom ? `${formatDate(c.periodFrom)} a ${formatDate(c.periodTo)}` : '—'}</KeyValue>
            <KeyValue label="Decisão do case">{formatDateTime(c.decidedAt)}</KeyValue>
            <KeyValue label="Enviada em">{formatDateTime(c.submittedAt)}</KeyValue>
            <KeyValue label="Protocolo COAF">{c.protocolNumber ?? '—'}</KeyValue>
            <KeyValue label="Parte">
              {c.partyId ? (
                <Link className="text-brand underline" to={`/partes/${c.partyId}`}>
                  {c.partyName ?? 'Ver parte'}
                </Link>
              ) : (
                '—'
              )}
            </KeyValue>
            <KeyValue label="Case de origem">
              {c.caseId ? (
                <Link className="text-brand underline" to={`/casos/${c.caseId}`}>
                  {c.caseNumber ?? `#${c.caseId.slice(0, 8)}`}
                </Link>
              ) : (
                '—'
              )}
            </KeyValue>
            <KeyValue label="Indícios (Carta Circular 4.001)">
              {indicators.length > 0 ? (
                <span className="flex flex-wrap gap-1">
                  {indicators.map((i) => (
                    <Badge key={i} tone="info">{i}</Badge>
                  ))}
                </span>
              ) : (
                '—'
              )}
            </KeyValue>
          </dl>
        </Card>
        <Card title="Prazo de envio (dia útil seguinte)">
          <SiscoafDeadlineBadge c={c} />
          {c.cancelReason && <p className="mt-3 text-sm">Motivo do cancelamento: {c.cancelReason}</p>}
        </Card>

        <Card title="Conteúdo (somente leitura)" className="lg:col-span-2">
          <PayloadView payload={c.payload} />
        </Card>
        <Card title="Histórico de status">
          <ol className="space-y-2 text-sm">
            {buildTimeline(c).map((h, i) => (
              <li key={`${h.status}-${i}`}>
                <span className="font-medium">{siscoafStatusLabel(h.status)}</span>
                {h.fromStatus && <span className="text-xs text-muted"> (de {siscoafStatusLabel(h.fromStatus)})</span>}
                <span className="block text-xs text-muted">
                  {formatDateTime(h.at)}
                  {h.byName ? ` · ${h.byName}` : ''}
                </span>
                {h.reason && <span className="block text-xs">Motivo: {h.reason}</span>}
              </li>
            ))}
          </ol>
        </Card>

        <Card title="Ações" className="lg:col-span-3">
          <Actions c={c} />
        </Card>
      </div>
    </>
  )
}
