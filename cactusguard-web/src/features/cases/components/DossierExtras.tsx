import { useRef, useState } from 'react'
import { Download, Paperclip, Send } from 'lucide-react'
import { downloadCaseEvidence } from '@/api/cases'
import type { CaseDossier, UUID } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card } from '@/components/ui/Layout'
import { toast } from '@/stores/toastStore'
import { errorMessage } from '@/api/errors'
import { directionLabel } from '@/features/parties/rules'
import { formatBRL, formatBytes, formatDateTime } from '@/lib/format'
import { useAuth } from '@/features/auth/useAuth'
import { useAddNote, useCaseEvidences, useCaseNotes, useUploadEvidence } from '../hooks'

export function EvidencesCard({ caseId, locked }: { caseId: UUID; locked: boolean }) {
  const { can } = useAuth()
  const q = useCaseEvidences(caseId)
  const upload = useUploadEvidence(caseId)
  const input = useRef<HTMLInputElement>(null)
  const [description, setDescription] = useState('')

  async function download(id: UUID, name: string) {
    try {
      const blob = await downloadCaseEvidence(caseId, id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = name
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }

  const writable = can('case:upload') && !locked

  return (
    <Card
      title="Evidências anexadas"
      actions={
        writable ? (
          <>
            <input
              ref={input}
              type="file"
              accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
              className="sr-only"
              aria-label="Selecionar arquivo de evidência"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) upload.mutate({ file, description }, { onSuccess: () => setDescription('') })
                e.target.value = ''
              }}
            />
            <Button variant="secondary" loading={upload.isPending} onClick={() => input.current?.click()}>
              <Paperclip size={14} aria-hidden /> Anexar
            </Button>
          </>
        ) : undefined
      }
    >
      {writable && (
        <div className="mb-3">
          <label htmlFor={`ev-desc-${caseId}`} className="mb-1 block text-xs text-muted">Descrição do próximo anexo (opcional)</label>
          <input id={`ev-desc-${caseId}`} value={description} maxLength={300} onChange={(e) => setDescription(e.target.value)} className="w-full rounded-lg border border-line px-3 py-2 text-sm" />
          <p className="mt-1 text-xs text-muted">PDF, JPG ou PNG, até 10 MB.</p>
        </div>
      )}
      {q.isLoading && <Skeleton className="h-12 w-full" />}
      {q.isError && <ErrorState error={q.error} compact />}
      {q.data && q.data.length === 0 && <EmptyState title="Nenhuma evidência anexada" />}
      {q.data && q.data.length > 0 && (
        <ul className="divide-y divide-line text-sm">
          {q.data.map((e) => (
            <li key={e.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
              <div>
                <p className="font-medium">{e.fileName}</p>
                {e.description && <p className="text-xs">{e.description}</p>}
                <p className="text-xs text-muted">
                  {formatBytes(e.sizeBytes)} · {e.uploadedByName} · {formatDateTime(e.uploadedAt)} · SHA-256 {e.sha256.slice(0, 12)}…
                </p>
              </div>
              <Button variant="ghost" onClick={() => void download(e.id, e.fileName)} aria-label={`Baixar ${e.fileName}`}>
                <Download size={14} aria-hidden /> Baixar
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

export function NotesCard({ caseId, locked }: { caseId: UUID; locked: boolean }) {
  const { can } = useAuth()
  const q = useCaseNotes(caseId)
  const add = useAddNote(caseId)
  const [text, setText] = useState('')

  return (
    <Card title="Notas e comentários">
      {q.isLoading && <Skeleton className="h-12 w-full" />}
      {q.isError && <ErrorState error={q.error} compact />}
      {q.data && q.data.length === 0 && <EmptyState title="Nenhuma nota registrada" />}
      {q.data && q.data.length > 0 && (
        <ul className="mb-3 space-y-2 text-sm">
          {q.data.map((n) => (
            <li key={n.id} className="rounded-lg bg-canvas p-2">
              <p>{n.text}</p>
              <p className="text-xs text-muted">{n.authorName} · {formatDateTime(n.createdAt)}</p>
            </li>
          ))}
        </ul>
      )}
      {q.data && can('case:note') && !locked && (
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            if (!text.trim()) return
            add.mutate(text.trim(), { onSuccess: () => setText('') })
          }}
        >
          <label className="sr-only" htmlFor={`note-${caseId}`}>Nova nota</label>
          <input id={`note-${caseId}`} value={text} onChange={(e) => setText(e.target.value)} placeholder="Escreva uma nota (imutável)" className="flex-1 rounded-lg border border-line px-3 py-2 text-sm" />
          <Button type="submit" loading={add.isPending} disabled={!text.trim()}>
            <Send size={14} aria-hidden /> Adicionar
          </Button>
        </form>
      )}
    </Card>
  )
}

/** Trilha de ações do case (até 200), já vinda do dossiê: disponível a quem acessa o dossiê. */
export function AuditTrailCard({ dossier }: { dossier: CaseDossier }) {
  const items = dossier.auditTrail ?? []
  return (
    <Card title="Trilha de auditoria do case">
      {items.length === 0 ? (
        <EmptyState title="Sem eventos registrados" />
      ) : (
        <ol className="space-y-2 text-sm">
          {items.map((e, i) => (
            <li key={`${e.createdAt}-${i}`} className="border-l-2 border-line pl-3">
              <span className="font-medium">{e.action}</span>
              <span className="text-muted"> · {e.userName ?? 'sistema'} · {formatDateTime(e.createdAt)}</span>
            </li>
          ))}
        </ol>
      )}
    </Card>
  )
}

/** Evidências do alerta de origem (parâmetro x limite x observado), do dossiê. */
export function AlertEvidenceCard({ dossier }: { dossier: CaseDossier }) {
  const ev = dossier.alert?.evidence ?? []
  return (
    <Card title={`Evidências do alerta${dossier.alert?.ruleCode ? ` (${dossier.alert.ruleCode}${dossier.alert.ruleVersion ? ` v${dossier.alert.ruleVersion}` : ''})` : ''}`}>
      {ev.length === 0 ? (
        <EmptyState title="Sem evidências do disparo" />
      ) : (
        <table className="w-full text-left text-sm">
          <thead className="text-xs uppercase text-muted">
            <tr><th scope="col" className="px-2 py-1">Parâmetro</th><th scope="col" className="px-2 py-1">Limite</th><th scope="col" className="px-2 py-1">Observado</th></tr>
          </thead>
          <tbody className="divide-y divide-line">
            {ev.map((e) => (
              <tr key={e.parameter}><td className="px-2 py-1.5">{e.label ?? e.parameter}</td><td className="px-2 py-1.5">{e.threshold ?? '—'}</td><td className="px-2 py-1.5 font-medium">{e.observed ?? '—'}</td></tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  )
}

/** Transações do alerta e do período analisado (máx. 200), do dossiê. */
export function DossierTransactionsCard({ dossier }: { dossier: CaseDossier }) {
  const [scope, setScope] = useState<'alert' | 'period'>('alert')
  const rows = (scope === 'alert' ? dossier.alertTransactions : dossier.periodTransactions) ?? []
  return (
    <Card
      title="Transações do dossiê"
      actions={
        <div role="group" aria-label="Escopo das transações" className="flex gap-1 text-xs">
          <Button variant={scope === 'alert' ? 'primary' : 'secondary'} onClick={() => setScope('alert')}>Do alerta</Button>
          <Button variant={scope === 'period' ? 'primary' : 'secondary'} onClick={() => setScope('period')}>Do período</Button>
        </div>
      }
    >
      {scope === 'period' && dossier.period && (
        <p className="mb-2 text-xs text-muted">Janela: {formatDateTime(dossier.period.from)} a {formatDateTime(dossier.period.to)}</p>
      )}
      {rows.length === 0 ? (
        <EmptyState title="Nenhuma transação" />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Transações</caption>
            <thead className="text-xs uppercase text-muted">
              <tr>
                <th scope="col" className="px-2 py-1">Data</th><th scope="col" className="px-2 py-1">Meio</th><th scope="col" className="px-2 py-1">Sentido</th>
                <th scope="col" className="px-2 py-1 text-right">Valor</th><th scope="col" className="px-2 py-1">Contraparte</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {rows.map((t) => (
                <tr key={t.id}>
                  <td className="px-2 py-1.5">{formatDateTime(t.transactionDate)}</td>
                  <td className="px-2 py-1.5">{t.paymentMethod ?? t.type}{t.cash && ' (espécie)'}</td>
                  <td className="px-2 py-1.5">{directionLabel(t.direction)}</td>
                  <td className="px-2 py-1.5 text-right tabular-nums">{formatBRL(t.amount)}</td>
                  <td className="px-2 py-1.5">{t.counterpartyName ?? '—'} {t.counterpartyDocument ?? ''}{t.counterpartyCountry && t.counterpartyCountry !== 'BR' ? ` · ${t.counterpartyCountry}` : ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
