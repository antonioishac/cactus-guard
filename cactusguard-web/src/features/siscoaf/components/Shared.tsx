import { Lock } from 'lucide-react'
import { Badge } from '@/components/ui/Badge'
import type { Tone } from '@/components/ui/Badge'
import { formatDateTime } from '@/lib/format'
import { formatRemaining } from '@/lib/deadline'
import type { SiscoafCommunication } from '@/api/types'
import { siscoafDeadline, siscoafStatusLabel, typeLabel } from '../deadline'

const STATUS_TONE: Record<string, Tone> = {
  DRAFT: 'neutral',
  PENDING_REVIEW: 'warning',
  APPROVED: 'info',
  SUBMITTED: 'success',
  ACKNOWLEDGED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'neutral',
  RECTIFIED: 'orange',
}

export function SiscoafStatusBadge({ status }: { status: string }) {
  return <Badge tone={STATUS_TONE[status] ?? 'neutral'}>{siscoafStatusLabel(status)}</Badge>
}

export function SiscoafTypeBadge({ type }: { type: string }) {
  return <Badge tone={type === 'COE' ? 'info' : type === 'NEGATIVE_DECLARATION' ? 'neutral' : 'orange'}>{typeLabel(type)}</Badge>
}

/** Semáforo do prazo de envio (dia útil seguinte): texto + cor. */
export function SiscoafDeadlineBadge({ c }: { c: Pick<SiscoafCommunication, 'deadlineAt' | 'status' | 'submittedAt'> }) {
  const r = siscoafDeadline(c)
  if (r.state === 'none') return <span className="text-xs text-muted">—</span>
  const label = r.state === 'done' ? (r.late ? 'Enviada fora do prazo' : 'Enviada no prazo') : r.state === 'overdue' ? 'Vencido' : r.state === 'warning' ? 'Vencendo' : 'No prazo'
  const tone: Tone = r.state === 'done' ? (r.late ? 'orange' : 'success') : r.state === 'overdue' ? 'danger' : r.state === 'warning' ? 'warning' : 'success'
  return (
    <span className="inline-flex flex-col gap-0.5" data-state={r.state}>
      <Badge tone={tone}>{label}</Badge>
      <span className="text-xs text-muted">
        {formatDateTime(c.deadlineAt)}
        {r.state !== 'done' && r.remainingMs !== undefined && ` · ${formatRemaining(r.remainingMs)}`}
      </span>
    </span>
  )
}

/** Aviso fixo: vedado dar ciência ao cliente (tipping-off). */
export function SecrecyNotice() {
  return (
    <div role="note" className="mb-4 flex items-start gap-3 rounded-xl border border-danger bg-danger-bg p-3 text-sm text-danger">
      <Lock size={18} className="mt-0.5 shrink-0" aria-hidden />
      <p>
        <strong>Sigilo obrigatório.</strong> É vedado dar ciência da existência desta comunicação ao cliente ou a terceiros (Lei 9.613/1998, art. 11, II). Não use canais voltados ao cliente.
      </p>
    </div>
  )
}
