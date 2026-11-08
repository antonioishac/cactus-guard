import { useParams } from 'react-router'
import { Badge, SeverityBadge } from '@/components/ui/Badge'
import { ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card, KeyValue, PageHeader } from '@/components/ui/Layout'
import { formatDateTime, formatNumber, formatPercent } from '@/lib/format'
import { RuleActions } from '../components/RuleActions'
import { RuleStatusBadge } from '../components/Shared'
import { VersionHistory } from '../components/VersionHistory'
import { paramsToFields } from '../params'
import { useRule, useRuleMetrics, useRuleVersions } from '../hooks'
import { categoryLabel } from '../ui-rules'

export function RuleDetailPage() {
  const { code } = useParams()
  const rule = useRule(code)
  const versions = useRuleVersions(code)
  const metrics = useRuleMetrics(code)

  if (rule.isLoading) return <Skeleton className="h-64 w-full" />
  if (rule.error) return <ErrorState error={rule.error} onRetry={() => void rule.refetch()} />
  const r = rule.data
  if (!r) return null
  const fields = paramsToFields(r.parameters)

  return (
    <>
      <PageHeader
        title={`${r.code} · v${r.version}`}
        subtitle={r.name}
        actions={
          <span className="flex items-center gap-2">
            <SeverityBadge severity={r.severity} />
            <RuleStatusBadge status={r.status} />
          </span>
        }
      />
      <div className="grid gap-4 lg:grid-cols-3">
        <Card title="Cenário" className="lg:col-span-2">
          {r.description && <p className="mb-3 text-sm">{r.description}</p>}
          <dl className="grid gap-4 sm:grid-cols-3">
            <KeyValue label="Cenário Carta 4.001">{r.carta4001Reference ?? '—'}</KeyValue>
            <KeyValue label="Categoria">{categoryLabel(r.category)}</KeyValue>
            <KeyValue label="Limiar">{r.thresholdValue != null ? formatNumber(r.thresholdValue) : '—'}</KeyValue>
            <KeyValue label="Janela (dias)">{r.thresholdPeriodDays ?? '—'}</KeyValue>
            <KeyValue label="Vigência">{formatDateTime(r.effectiveFrom)} até {r.effectiveTo ? formatDateTime(r.effectiveTo) : 'vigente'}</KeyValue>
            <KeyValue label="Níveis de risco">
              {r.appliesToRiskLevels?.length ? <span className="flex gap-1">{r.appliesToRiskLevels.map((l) => <Badge key={l} tone="info">{l}</Badge>)}</span> : 'Todos'}
            </KeyValue>
            <KeyValue label="Criada/alterada por">{r.createdByName ?? '—'}</KeyValue>
            <KeyValue label="Aprovada por">{r.approvedByName ? `${r.approvedByName} em ${formatDateTime(r.approvedAt)}` : '—'}</KeyValue>
            <KeyValue label="Versão vigente">{r.activeVersion != null ? `v${r.activeVersion}` : 'Nenhuma'}</KeyValue>
            <KeyValue label="Comunicação automática">{r.autoCommunication ? 'Sim (COE, sem análise)' : 'Não'}</KeyValue>
          </dl>
        </Card>
        <Card title="Métricas (últimos 30 dias)">
          {metrics.isLoading && <Skeleton className="h-24 w-full" />}
          {metrics.isError && <ErrorState error={metrics.error} compact />}
          {metrics.data && (
            <dl className="grid gap-4">
              <KeyValue label="Alertas gerados">{formatNumber(metrics.data.alertsGenerated)}</KeyValue>
              <KeyValue label="Alertas encerrados">{formatNumber(metrics.data.alertsClosed)}</KeyValue>
              <KeyValue label="Falsos positivos">{formatNumber(metrics.data.falsePositives)}</KeyValue>
              <KeyValue label="Taxa de falso positivo">{metrics.data.falsePositiveRate != null ? formatPercent(metrics.data.falsePositiveRate * 100) : 'Sem alertas encerrados'}</KeyValue>
            </dl>
          )}
        </Card>

        <Card title="Parâmetros da versão" className="lg:col-span-3">
          {fields.length === 0 ? (
            <p className="text-sm text-muted">Sem parâmetros.</p>
          ) : (
            <dl className="grid gap-3 sm:grid-cols-3">
              {fields.map((f) => (
                <KeyValue key={f.path} label={f.path}>{f.kind === 'boolean' ? (f.value === 'true' ? 'Sim' : 'Não') : f.value}</KeyValue>
              ))}
            </dl>
          )}
        </Card>

        <Card title="Histórico de versões" className="lg:col-span-3">
          {versions.isLoading ? <Skeleton className="h-24 w-full" /> : versions.error ? <ErrorState error={versions.error} compact onRetry={() => void versions.refetch()} /> : <VersionHistory versions={versions.data ?? []} />}
        </Card>

        <Card title="Ações" className="lg:col-span-3">
          <RuleActions rule={r} />
        </Card>
      </div>
    </>
  )
}
