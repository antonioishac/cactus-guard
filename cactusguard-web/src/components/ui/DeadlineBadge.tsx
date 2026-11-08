import { evaluateDeadline, formatRemaining, WARN_WINDOW_MS } from '@/lib/deadline'
import type { DeadlineState } from '@/lib/deadline'
import { formatDateTime } from '@/lib/format'
import { Badge } from './Badge'
import type { Tone } from './Badge'

export type DeadlineStage = keyof typeof WARN_WINDOW_MS

const TONE: Record<DeadlineState, Tone> = { ok: 'success', warning: 'warning', overdue: 'danger', done: 'neutral', none: 'neutral' }
const LABEL: Record<DeadlineState, string> = { ok: 'No prazo', warning: 'Vencendo', overdue: 'Vencido', done: 'Cumprido', none: 'Sem prazo' }

/**
 * Selo de prazo PROVISÓRIO (classificação no cliente). Só resta para o prazo de seleção do alerta e a comunicação
 * SISCOAF, cujos DTOs ainda não trazem o estado (slaStatus). Cases usam `SlaBadge` (estado do backend).
 */
export function DeadlineBadge({ stage, deadline, doneAt }: { stage: DeadlineStage; deadline: string | null | undefined; doneAt?: string | null }) {
  const r = evaluateDeadline({ deadline, doneAt, warnMs: WARN_WINDOW_MS[stage] })
  const label = r.state === 'done' && r.late ? 'Cumprido fora do prazo' : LABEL[r.state]
  const tone: Tone = r.state === 'done' && r.late ? 'orange' : TONE[r.state]
  return (
    <span className="inline-flex flex-col gap-0.5">
      <Badge tone={tone} title={deadline ? `Prazo: ${formatDateTime(deadline)}` : undefined}>{label}</Badge>
      {deadline && (
        <span className="text-xs text-muted">
          {formatDateTime(deadline)}
          {r.state !== 'done' && r.remainingMs !== undefined && ` · ${formatRemaining(r.remainingMs)}`}
        </span>
      )}
    </span>
  )
}
