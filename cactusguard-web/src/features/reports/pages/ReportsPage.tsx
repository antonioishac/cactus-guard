import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { errorMessage } from '@/api/errors'
import type { ReportDefinition, ReportFormat, ReportJob } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { DataTable } from '@/components/ui/DataTable'
import type { Column } from '@/components/ui/DataTable'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card, PageHeader } from '@/components/ui/Layout'
import { Modal } from '@/components/ui/Modal'
import { useListState } from '@/hooks/useListState'
import { formatDate, formatDateTime } from '@/lib/format'
import { DownloadButton, JobStatusBadge } from '../components/JobParts'
import { useGenerateReport, useMyReports, useReportDefinitions, useReportJob } from '../hooks'

const CATEGORY: Record<string, string> = { OPERATIONAL: 'Operacional', COMPLIANCE: 'Conformidade', REGULATORY: 'Regulatório', AUDIT: 'Auditoria' }
const today = () => new Date().toISOString().slice(0, 10)
const daysAgo = (n: number) => {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d.toISOString().slice(0, 10)
}

const schema = z
  .object({
    format: z.enum(['PDF', 'EXCEL']),
    from: z.string().min(1, 'Informe a data inicial.'),
    to: z.string().min(1, 'Informe a data final.'),
  })
  .refine((v) => v.from <= v.to, { path: ['to'], message: 'A data final deve ser igual ou posterior à inicial.' })
  .refine((v) => v.to <= today(), { path: ['to'], message: 'A data final não pode estar no futuro.' })
type FormValues = z.infer<typeof schema>

function GenerateDialog({ def, onClose, onStarted }: { def: ReportDefinition | null; onClose: () => void; onStarted: (job: ReportJob) => void }) {
  const gen = useGenerateReport()
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { format: 'PDF', from: daysAgo(30), to: today() },
  })
  const close = () => {
    reset()
    onClose()
  }
  const submit = handleSubmit(async (v) => {
    if (!def) return
    try {
      const job = await gen.mutateAsync({ code: def.code, body: { format: v.format as ReportFormat, parameters: { from: v.from, to: v.to } } })
      onStarted(job)
      close()
    } catch (e) {
      setError('root', { message: errorMessage(e) })
    }
  })
  return (
    <Modal open={!!def} title={def ? `Gerar: ${def.name}` : ''} onClose={close}>
      <form onSubmit={(e) => void submit(e)} noValidate className="space-y-4">
        <fieldset>
          <legend className="mb-1 text-sm font-medium">Formato</legend>
          <div className="flex gap-4">
            {(['PDF', 'EXCEL'] as const).map((f) => (
              <label key={f} className="flex items-center gap-1.5 text-sm">
                <input type="radio" value={f} {...register('format')} /> {f === 'PDF' ? 'PDF' : 'Excel'}
              </label>
            ))}
          </div>
        </fieldset>
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label htmlFor="rp-from" className="mb-1 block text-sm font-medium">Período: de</label>
            <input id="rp-from" type="date" {...register('from')} max={today()} className="w-full rounded-lg border border-line px-2.5 py-1.5 text-sm" />
            {errors.from && <p role="alert" className="mt-1 text-xs text-danger">{errors.from.message}</p>}
          </div>
          <div>
            <label htmlFor="rp-to" className="mb-1 block text-sm font-medium">Período: até</label>
            <input id="rp-to" type="date" {...register('to')} max={today()} className="w-full rounded-lg border border-line px-2.5 py-1.5 text-sm" />
            {errors.to && <p role="alert" className="mt-1 text-xs text-danger">{errors.to.message}</p>}
          </div>
        </div>
        {errors.root && <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">{errors.root.message}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={close}>Cancelar</Button>
          <Button type="submit" loading={isSubmitting}>Gerar relatório</Button>
        </div>
      </form>
    </Modal>
  )
}

/** Acompanha um job recém-solicitado até COMPLETED/FAILED. */
function TrackedJob({ id }: { id: string }) {
  const q = useReportJob(id)
  if (!q.data) return q.error ? <ErrorState error={q.error} compact /> : <Skeleton className="h-6 w-64" />
  const j = q.data
  return (
    <div className="flex flex-wrap items-center gap-3 text-sm" role="status" aria-live="polite">
      <span className="font-medium">{j.reportCode}</span>
      <Badge>{j.format}</Badge>
      <JobStatusBadge status={j.status} />
      {j.status === 'FAILED' && <span className="text-danger">{j.errorMessage ?? 'A geração falhou. Tente novamente ou contate o suporte.'}</span>}
      <DownloadButton job={j} />
    </div>
  )
}

const KEYS = [] as const

const columns: Column<ReportJob>[] = [
  { key: 'report', header: 'Relatório', render: (j) => j.reportCode },
  { key: 'format', header: 'Formato', render: (j) => j.format },
  { key: 'period', header: 'Período', render: (j) => (j.parameters?.from ? `${formatDate(String(j.parameters.from))} a ${formatDate(String(j.parameters.to ?? ''))}` : '—') },
  { key: 'status', header: 'Status', render: (j) => <JobStatusBadge status={j.status} /> },
  { key: 'created', header: 'Solicitado em', sortField: 'createdAt', render: (j) => formatDateTime(j.createdAt) },
  { key: 'done', header: 'Concluído em', render: (j) => formatDateTime(j.completedAt) },
  { key: 'dl', header: 'Arquivo', render: (j) => <DownloadButton job={j} /> },
]

export function ReportsPage() {
  const defs = useReportDefinitions()
  const [selected, setSelected] = useState<ReportDefinition | null>(null)
  const [tracked, setTracked] = useState<string[]>([])
  const list = useListState(KEYS, 'createdAt,desc')
  const mine = useMyReports({ page: list.page, size: list.size, sort: list.sort })
  const active = (defs.data ?? []).filter((d) => d.active)

  return (
    <>
      <PageHeader title="Relatórios" subtitle="Gere relatórios em PDF ou Excel e acompanhe o processamento" />

      <Card title="Catálogo" className="mb-4">
        {defs.isLoading && <Skeleton className="h-24 w-full" />}
        {defs.error && <ErrorState error={defs.error} onRetry={() => void defs.refetch()} />}
        {defs.data && active.length === 0 && <EmptyState title="Nenhum relatório disponível" />}
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {active.map((d) => (
            <li key={d.code} className="flex flex-col justify-between gap-3 rounded-lg border border-line p-3">
              <div>
                <p className="font-medium">{d.name}</p>
                {d.category && <Badge tone="info">{CATEGORY[d.category] ?? d.category}</Badge>}
                {d.description && <p className="mt-1 text-sm text-muted">{d.description}</p>}
              </div>
              <Button variant="secondary" onClick={() => setSelected(d)} aria-label={`Gerar ${d.name}`}>Gerar</Button>
            </li>
          ))}
        </ul>
      </Card>

      {tracked.length > 0 && (
        <Card title="Em acompanhamento" className="mb-4">
          <ul className="space-y-2">
            {tracked.map((id) => (
              <li key={id}>
                <TrackedJob id={id} />
              </li>
            ))}
          </ul>
        </Card>
      )}

      <h2 className="mb-2 text-lg font-semibold">Meus relatórios</h2>
      <DataTable
        caption="Meus relatórios"
        columns={columns}
        page={mine.data}
        loading={mine.isLoading}
        error={mine.error}
        onRetry={() => void mine.refetch()}
        rowKey={(j) => j.id}
        sort={list.sort}
        onSort={list.setSort}
        onPage={list.setPage}
        emptyTitle="Você ainda não gerou relatórios"
      />

      <GenerateDialog def={selected} onClose={() => setSelected(null)} onStarted={(j) => setTracked((t) => [j.id, ...t].slice(0, 5))} />
    </>
  )
}
