import type { SlaStatus } from '@/api/types'
import { formatDateTime } from '@/lib/format'
import { Badge } from './Badge'
import type { Tone } from './Badge'

const TONE: Record<SlaStatus, Tone> = { ON_TIME: 'success', DUE_SOON: 'warning', OVERDUE: 'danger' }
const LABEL: Record<SlaStatus, string> = { ON_TIME: 'No prazo', DUE_SOON: 'Vencendo', OVERDUE: 'Vencido' }

/** Prazo vigente do case (análise ou comunicação). O estado vem pronto do backend (`slaStatus`); o front só exibe. */
export function SlaBadge({ slaStatus, deadline, phase }: { slaStatus: SlaStatus | null | undefined; deadline?: string | null; phase?: string }) {
  return (
    <span className="inline-flex flex-col gap-0.5">
      {slaStatus ? <Badge tone={TONE[slaStatus]} title={phase}>{LABEL[slaStatus]}</Badge> : <Badge tone="neutral">Encerrado</Badge>}
      {slaStatus && deadline && (
        <span className="text-xs text-muted">
          {phase ? `${phase}: ` : ''}
          {formatDateTime(deadline)}
        </span>
      )}
    </span>
  )
}
