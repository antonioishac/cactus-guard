import { RefreshCw } from 'lucide-react'
import type { Party, RiskHistoryEntry } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card, KeyValue } from '@/components/ui/Layout'
import { useAuth } from '@/features/auth/useAuth'
import { formatDate, formatDateTime } from '@/lib/format'
import { useRecalculateRisk, useRiskTimeline } from '../../hooks'
import { RISK_TONE, riskLabel } from '../../rules'

const W = 560
const H = 120
const PAD = 16

/** Gráfico de linha em SVG (score 0-100 ao longo do tempo). `entries` em ordem cronológica. */
export function RiskChart({ entries }: { entries: RiskHistoryEntry[] }) {
  if (entries.length < 2) return null
  const pts = entries.map((e, i) => {
    const x = PAD + (i * (W - 2 * PAD)) / (entries.length - 1)
    const y = H - PAD - (Math.min(Math.max(e.newScore, 0), 100) / 100) * (H - 2 * PAD)
    return { x, y, e }
  })
  const label = `Evolução do score de risco: de ${entries[0]!.newScore} a ${entries[entries.length - 1]!.newScore} em ${entries.length} registros`
  return (
    <svg role="img" aria-label={label} viewBox={`0 0 ${W} ${H}`} className="h-32 w-full">
      <line x1={PAD} y1={H - PAD} x2={W - PAD} y2={H - PAD} stroke="var(--color-line)" />
      <polyline fill="none" stroke="var(--color-brand)" strokeWidth={2} points={pts.map((p) => `${p.x},${p.y}`).join(' ')} />
      {pts.map((p) => (
        <circle key={p.e.id} cx={p.x} cy={p.y} r={3.5} fill="var(--color-brand)">
          <title>{`${formatDateTime(p.e.createdAt)}: ${p.e.newScore} (${riskLabel(p.e.newLevel)})`}</title>
        </circle>
      ))}
    </svg>
  )
}

export function RiskTab({ party }: { party: Party }) {
  const { can } = useAuth()
  const q = useRiskTimeline(party.id)
  const recalc = useRecalculateRisk(party.id)
  const level = party.riskLevel ?? ''
  const entries = q.data?.content ?? []
  const chrono = [...entries].reverse()

  return (
    <div className="space-y-4">
      <Card
        title="Classificação de risco atual"
        actions={can('risk:recalculate') ? <Button variant="secondary" loading={recalc.isPending} onClick={() => recalc.mutate()}><RefreshCw size={14} aria-hidden /> Recalcular risco</Button> : undefined}
      >
        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <KeyValue label="Score">{party.riskScore ?? '—'}</KeyValue>
          <KeyValue label="Nível"><Badge tone={RISK_TONE[level] ?? 'neutral'}>{riskLabel(level)}</Badge></KeyValue>
          <KeyValue label="Próxima revisão">{formatDate(party.nextReviewDate)}</KeyValue>
          <KeyValue label="EDD (diligência reforçada)">{party.eddRequired ? <Badge tone="warning">Requerida</Badge> : 'Não requerida'}</KeyValue>
          <KeyValue label="Calculado em">{formatDateTime(party.riskCalculatedAt)}</KeyValue>
        </dl>
        <div className="mt-3">
          <h3 className="mb-1 text-sm font-semibold">Fatores</h3>
          <p className="text-sm text-muted">Os fatores do score ficam no motivo de cada alteração, no histórico abaixo.</p>
        </div>
      </Card>

      <Card title="Histórico de classificação">
        {q.isLoading && <Skeleton className="h-24 w-full" />}
        {q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} compact />}
        {q.data && entries.length === 0 && <EmptyState title="Sem alterações de risco registradas." />}
        {q.data && entries.length > 0 && (
          <>
            <RiskChart entries={chrono} />
            <ol className="mt-2 space-y-2 text-sm">
              {entries.map((r) => (
                <li key={r.id} className="border-l-2 border-line pl-3">
                  <span className="font-medium">{riskLabel(r.previousLevel)} &rarr; {riskLabel(r.newLevel)}</span>
                  <span className="text-muted"> · score {r.previousScore ?? '—'} &rarr; {r.newScore} · {formatDateTime(r.createdAt)}</span>
                  {r.reason && <p className="text-muted">{r.reason}</p>}
                </li>
              ))}
            </ol>
          </>
        )}
      </Card>
    </div>
  )
}
