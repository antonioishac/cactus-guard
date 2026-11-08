import { useRef, useState } from 'react'
import { Download, Paperclip, Send } from 'lucide-react'
import { downloadCaseEvidence } from '@/api/cases'
import type { UUID } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card } from '@/components/ui/Layout'
import { toast } from '@/stores/toastStore'
import { errorMessage } from '@/api/errors'
import { formatBytes, formatDateTime } from '@/lib/format'
import { useAuth } from '@/features/auth/useAuth'
import { useAuditTrail } from '@/features/parties/hooks'
import { useAddNote, useCaseEvidences, useCaseNotes, useUploadEvidence } from '../hooks'

export function EvidencesCard({ caseId, locked }: { caseId: UUID; locked: boolean }) {
  const { can } = useAuth()
  const q = useCaseEvidences(caseId)
  const upload = useUploadEvidence(caseId)
  const input = useRef<HTMLInputElement>(null)

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

  return (
    <Card
      title="Evidências anexadas"
      actions={
        can('case:upload') && !locked ? (
          <>
            <input
              ref={input}
              type="file"
              className="sr-only"
              aria-label="Selecionar arquivo de evidência"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) upload.mutate(file)
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
      {q.isLoading && <Skeleton className="h-12 w-full" />}
      {q.isError && <ErrorState error={q.error} compact />}
      {q.data && q.data.length === 0 && <EmptyState title="Nenhuma evidência anexada" />}
      {q.data && q.data.length > 0 && (
        <ul className="divide-y divide-line text-sm">
          {q.data.map((e) => (
            <li key={e.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
              <div>
                <p className="font-medium">{e.fileName}</p>
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
          <input id={`note-${caseId}`} value={text} onChange={(e) => setText(e.target.value)} placeholder="Escreva uma nota" className="flex-1 rounded-lg border border-line px-3 py-2 text-sm" />
          <Button type="submit" loading={add.isPending} disabled={!text.trim()}>
            <Send size={14} aria-hidden /> Adicionar
          </Button>
        </form>
      )}
    </Card>
  )
}

export function AuditTrailCard({ caseId }: { caseId: UUID }) {
  const { can } = useAuth()
  const allowed = can('audit:read')
  const q = useAuditTrail('CASE', caseId, allowed)
  return (
    <Card title="Trilha de auditoria">
      {!allowed && <p className="text-sm text-muted">Disponível para Supervisor, Auditor e Administrador.</p>}
      {allowed && q.isLoading && <Skeleton className="h-12 w-full" />}
      {allowed && q.isError && <ErrorState error={q.error} compact />}
      {q.data && q.data.content.length === 0 && <EmptyState title="Sem eventos registrados" />}
      {q.data && q.data.content.length > 0 && (
        <ol className="space-y-2 text-sm">
          {q.data.content.map((e) => (
            <li key={e.id} className="border-l-2 border-line pl-3">
              <span className="font-medium">{e.action}</span>
              <span className="text-muted"> · {e.user?.name ?? e.user?.username ?? 'sistema'} · {formatDateTime(e.createdAt)}</span>
            </li>
          ))}
        </ol>
      )}
    </Card>
  )
}
