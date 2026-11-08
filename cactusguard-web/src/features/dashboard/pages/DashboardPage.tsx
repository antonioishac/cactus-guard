import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { Bell, FolderOpen, RefreshCw, ShieldAlert, UserCheck } from 'lucide-react'
import { CartesianGrid, Cell, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis, Legend } from 'recharts'
import type { Alert, DeadlineBuckets, Severity } from '@/api/types'
import { SeverityBadge, StatusBadge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { DeadlineBadge } from '@/components/ui/DeadlineBadge'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card, PageHeader } from '@/components/ui/Layout'
import { formatDate, formatNumber, severityLabel, statusLabel } from '@/lib/format'
import { useAlertsChart, useCaseSla, useCriticalAlerts, useMetrics, useOperational, useRecentAlerts, useSeverityDistribution } from '../hooks'

const SEVERITY_COLOR: Record<Severity, string> = {
  CRITICAL: 'var(--color-chart-critical)',
  HIGH: 'var(--color-chart-high)',
  MEDIUM: 'var(--color-chart-medium)',
  LOW: 'var(--color-chart-low)',
}

function MetricCard({ label, value, icon, tone, to, loading }: {
  label: string
  value: number | undefined
  icon: ReactNode
  tone: string
  to: string
  loading: boolean
}) {
  return (
    <Link to={to} className="rounded-xl border border-line bg-surface p-4 transition-shadow hover:shadow-md">
      <div className="flex items-center justify-between">
        <span className="text-sm text-muted">{label}</span>
        <span className={tone} aria-hidden>{icon}</span>
      </div>
      {loading ? <Skeleton className="mt-2 h-8 w-16" /> : <p className="mt-1 text-3xl font-semibold tabular-nums">{formatNumber(value)}</p>}
    </Link>
  )
}

function SlaRow({ label, b }: { label: string; b: DeadlineBuckets }) {
  return (
    <tr>
      <th scope="row" className="py-2 pr-2 text-left font-normal">{label}</th>
      <td className="px-2 text-center"><Link className="text-success underline" to={`/casos?slaStatus=ON_TIME`}>{b.onTime}</Link></td>
      <td className="px-2 text-center"><Link className="text-warning underline" to={`/casos?slaStatus=DUE_SOON`}>{b.dueSoon}</Link></td>
      <td className="px-2 text-center"><Link className="font-semibold text-danger underline" to={`/casos?slaStatus=OVERDUE`}>{b.overdue}</Link></td>
    </tr>
  )
}

function AlertsMiniTable({ rows }: { rows: Alert[] }) {
  if (rows.length === 0) return <EmptyState title="Nenhum alerta" />
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="text-xs uppercase text-muted">
          <tr>
            <th scope="col" className="px-2 py-1">Alerta</th>
            <th scope="col" className="px-2 py-1">Parte</th>
            <th scope="col" className="px-2 py-1">Severidade</th>
            <th scope="col" className="px-2 py-1">Status</th>
            <th scope="col" className="px-2 py-1">Prazo de seleção</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rows.map((a) => (
            <tr key={a.id}>
              <td className="px-2 py-1.5"><Link className="text-brand underline" to={`/alertas/${a.id}`}>{a.title ?? a.type}</Link></td>
              <td className="px-2 py-1.5">{a.partyName ?? '—'}</td>
              <td className="px-2 py-1.5"><SeverityBadge severity={a.severity} /></td>
              <td className="px-2 py-1.5"><StatusBadge status={a.status} /></td>
              <td className="px-2 py-1.5"><DeadlineBadge stage="selection" deadline={a.selectionDeadline} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function DashboardPage() {
  const qc = useQueryClient()
  const op = useOperational()
  const metrics = useMetrics()
  const chart = useAlertsChart()
  const sev = useSeverityDistribution()
  const sla = useCaseSla()
  const recent = useRecentAlerts()
  const critical = useCriticalAlerts()

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle="Visão operacional (atualiza a cada minuto)"
        actions={
          <Button variant="secondary" onClick={() => void qc.invalidateQueries({ queryKey: ['dashboard'] })}>
            <RefreshCw size={14} aria-hidden /> Atualizar
          </Button>
        }
      />

      {op.isError && <ErrorState error={op.error} onRetry={() => void op.refetch()} compact />}
      <div className="mb-4 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <MetricCard label="Alertas abertos" value={op.data?.openAlerts} loading={op.isLoading} icon={<Bell size={20} />} tone="text-danger" to="/alertas?status=OPEN" />
        <MetricCard label="Cases em andamento" value={op.data?.openCases} loading={op.isLoading} icon={<FolderOpen size={20} />} tone="text-warning" to="/casos" />
        <MetricCard label="Alertas críticos abertos" value={op.data?.criticalAlerts} loading={op.isLoading} icon={<ShieldAlert size={20} />} tone="text-danger" to="/alertas?status=OPEN&severity=CRITICAL" />
        <MetricCard label="KYC pendentes" value={op.data?.pendingKyc} loading={op.isLoading} icon={<UserCheck size={20} />} tone="text-neutral" to="/partes" />
      </div>

      <div className="mb-4 grid gap-4 lg:grid-cols-3">
        <Card title="Alertas ao longo do tempo (7 dias)" className="lg:col-span-2">
          {chart.isLoading && <Skeleton className="h-56 w-full" />}
          {chart.isError && <ErrorState error={chart.error} compact />}
          {chart.data && (
            <div className="h-56" role="img" aria-label="Gráfico de linha: total de alertas por dia nos últimos 7 dias">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={chart.data.map((p) => ({ ...p, label: formatDate(p.date) }))}>
                  <CartesianGrid stroke="var(--color-line)" strokeDasharray="3 3" />
                  <XAxis dataKey="label" fontSize={12} />
                  <YAxis allowDecimals={false} fontSize={12} />
                  <Tooltip />
                  <Line type="monotone" dataKey="total" name="Alertas" stroke="var(--color-accent)" strokeWidth={2} dot />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>

        <Card title="Distribuição por severidade">
          {sev.isLoading && <Skeleton className="h-56 w-full" />}
          {sev.isError && <ErrorState error={sev.error} compact />}
          {sev.data && (
            <div className="h-56" role="img" aria-label="Gráfico de rosca: alertas por severidade">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={sev.data.map((s) => ({ ...s, name: severityLabel(s.severity) }))} dataKey="count" nameKey="name" innerRadius={50} outerRadius={80}>
                    {sev.data.map((s) => <Cell key={s.severity} fill={SEVERITY_COLOR[s.severity]} />)}
                  </Pie>
                  <Tooltip />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>
      </div>

      <div className="mb-4 grid gap-4 lg:grid-cols-3">
        <Card title="Cases por prazo regulatório">
          {sla.isLoading && <Skeleton className="h-28 w-full" />}
          {sla.isError && <ErrorState error={sla.error} compact />}
          {sla.data && (
            <>
              <table className="w-full text-sm">
                <thead className="text-xs text-muted">
                  <tr><th scope="col" className="text-left font-medium">Etapa</th><th scope="col">No prazo</th><th scope="col">Vencendo</th><th scope="col">Vencido</th></tr>
                </thead>
                <tbody className="divide-y divide-line">
                  <SlaRow label="Seleção (45 d)" b={sla.data.selection} />
                  <SlaRow label="Análise (45 d)" b={sla.data.analysis} />
                  <SlaRow label="Comunicação (D+1)" b={sla.data.communication} />
                </tbody>
              </table>
              <p className="mt-3 text-sm">
                <Link className="text-brand underline" to="/casos?status=PENDING_APPROVAL">Aguardando aprovação: <strong>{sla.data.pendingApproval}</strong></Link>
              </p>
            </>
          )}
        </Card>

        <Card title="Alertas por status">
          {metrics.isLoading && <Skeleton className="h-28 w-full" />}
          {metrics.isError && <ErrorState error={metrics.error} compact />}
          {metrics.data && (
            <ul className="space-y-1 text-sm">
              {Object.entries(metrics.data.alertsByStatus).map(([s, n]) => (
                <li key={s} className="flex justify-between">
                  <Link className="underline" to={`/alertas?status=${s}`}>{statusLabel(s)}</Link>
                  <span className="tabular-nums">{formatNumber(n)}</span>
                </li>
              ))}
              {metrics.data.activePeps != null && <li className="flex justify-between border-t border-line pt-1"><span>PEPs ativos</span><span className="tabular-nums">{formatNumber(metrics.data.activePeps)}</span></li>}
              {metrics.data.screenings != null && <li className="flex justify-between"><span>Screenings</span><span className="tabular-nums">{formatNumber(metrics.data.screenings)}</span></li>}
            </ul>
          )}
        </Card>

        <Card title="Alertas críticos (mais urgentes)">
          {critical.isLoading && <Skeleton className="h-28 w-full" />}
          {critical.isError && <ErrorState error={critical.error} compact />}
          {critical.data && (
            critical.data.content.length === 0 ? <EmptyState title="Nenhum alerta crítico aberto" /> : (
              <ul className="space-y-2 text-sm">
                {critical.data.content.map((a) => (
                  <li key={a.id}>
                    <Link className="text-brand underline" to={`/alertas/${a.id}`}>{a.title ?? a.type}</Link>
                    <span className="block text-xs text-muted">{a.partyName ?? '—'}</span>
                  </li>
                ))}
              </ul>
            )
          )}
        </Card>
      </div>

      <Card title="Alertas recentes" actions={<Link className="text-sm text-brand underline" to="/alertas">Ver todos</Link>}>
        {recent.isLoading && <Skeleton className="h-24 w-full" />}
        {recent.isError && <ErrorState error={recent.error} onRetry={() => void recent.refetch()} compact />}
        {recent.data && <AlertsMiniTable rows={recent.data.content} />}
      </Card>
    </>
  )
}
