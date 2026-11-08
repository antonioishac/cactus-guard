import { useRef, useState } from 'react'
import { CheckCircle2, Circle, Download, Paperclip } from 'lucide-react'
import { downloadKycDocument } from '@/api/kyc'
import { errorMessage } from '@/api/errors'
import { KYC_DOCUMENT_TYPES } from '@/api/types'
import type { KycProcess, UUID } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import type { Tone } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card } from '@/components/ui/Layout'
import { useAuth } from '@/features/auth/useAuth'
import { formatDate, formatDateTime } from '@/lib/format'
import { toast } from '@/stores/toastStore'
import { useApproveDocument, useApproveKyc, useChecklist, useCompleteItem, useKycProcesses, useKycDocuments, useRejectKyc, useStartOnboarding, useUploadDocument } from '../hooks'
import { validateKycFile } from '../upload'

const KYC_STATUS: Record<string, { label: string; tone: Tone }> = {
  IN_PROGRESS: { label: 'Em andamento', tone: 'info' },
  APPROVED: { label: 'Aprovado', tone: 'success' },
  REJECTED: { label: 'Reprovado', tone: 'danger' },
  EXPIRED: { label: 'Expirado', tone: 'warning' },
}
const DOC_TYPE_LABEL: Record<string, string> = {
  RG: 'RG', CNH: 'CNH', CPF: 'CPF', COMPROVANTE_RESIDENCIA: 'Comprovante de residência', CONTRATO_SOCIAL: 'Contrato social',
  CARTAO_CNPJ: 'Cartão CNPJ', COMPROVANTE_RENDA: 'Comprovante de renda', OUTROS: 'Outros',
}

function Checklist({ processId, editable }: { processId: UUID; editable: boolean }) {
  const q = useChecklist(processId)
  const complete = useCompleteItem()
  return (
    <div>
      <h4 className="mb-1 text-sm font-semibold">Checklist</h4>
      {q.isLoading && <Skeleton className="h-10 w-full" />}
      {q.isError && <ErrorState error={q.error} compact />}
      {q.data && q.data.length === 0 && <p className="text-sm text-muted">Sem itens.</p>}
      <ul className="space-y-1 text-sm">
        {q.data?.map((i) => (
          <li key={i.id} className="flex items-center justify-between gap-2">
            <span className="inline-flex items-center gap-2">
              {i.completed ? <CheckCircle2 size={16} className="text-success" aria-label="Concluído" /> : <Circle size={16} className="text-muted" aria-label="Pendente" />}
              {i.itemDescription}
              {i.required && <Badge tone="neutral">Obrigatório</Badge>}
            </span>
            {!i.completed && editable && (
              <Button variant="ghost" className="!px-2 !py-1 text-xs" loading={complete.isPending && complete.variables === i.id} onClick={() => complete.mutate(i.id)}>Concluir</Button>
            )}
            {i.completedAt && <span className="text-xs text-muted">{formatDateTime(i.completedAt)}</span>}
          </li>
        ))}
      </ul>
    </div>
  )
}

function Documents({ processId, editable, canApprove }: { processId: UUID; editable: boolean; canApprove: boolean }) {
  const q = useKycDocuments(processId)
  const upload = useUploadDocument()
  const approve = useApproveDocument()
  const input = useRef<HTMLInputElement>(null)
  const [type, setType] = useState<string>(KYC_DOCUMENT_TYPES[0])
  const [fileError, setFileError] = useState<string | null>(null)

  async function download(id: UUID, name: string) {
    try {
      const blob = await downloadKycDocument(id)
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

  function onFile(file: File | undefined) {
    if (!file) return
    const problem = validateKycFile(file)
    setFileError(problem)
    if (!problem) upload.mutate({ processId, documentType: type, file })
  }

  return (
    <div>
      <h4 className="mb-1 text-sm font-semibold">Documentos</h4>
      {q.isLoading && <Skeleton className="h-10 w-full" />}
      {q.isError && <ErrorState error={q.error} compact />}
      {q.data && q.data.length === 0 && <p className="text-sm text-muted">Nenhum documento enviado.</p>}
      <ul className="divide-y divide-line text-sm">
        {q.data?.map((d) => (
          <li key={d.id} className="flex flex-wrap items-center justify-between gap-2 py-1.5">
            <span>
              <span className="font-medium">{d.fileName}</span>
              <span className="text-muted"> · {DOC_TYPE_LABEL[d.documentType] ?? d.documentType} · {formatDate(d.createdAt)}</span>
              <span className="ml-2"><Badge tone={d.status === 'APPROVED' ? 'success' : 'neutral'}>{d.status === 'APPROVED' ? 'Aprovado' : d.status === 'PENDING' ? 'Pendente' : d.status}</Badge></span>
            </span>
            <span className="flex gap-1">
              {canApprove && d.status !== 'APPROVED' && <Button variant="secondary" className="!px-2 !py-1 text-xs" onClick={() => approve.mutate(d.id)}>Aprovar documento</Button>}
              <Button variant="ghost" className="!px-2 !py-1 text-xs" onClick={() => void download(d.id, d.fileName)} aria-label={`Baixar ${d.fileName}`}><Download size={14} aria-hidden /> Baixar</Button>
            </span>
          </li>
        ))}
      </ul>
      {editable && (
        <div className="mt-3 flex flex-wrap items-end gap-2">
          <div>
            <label htmlFor={`doctype-${processId}`} className="mb-1 block text-xs font-medium text-muted">Tipo do documento</label>
            <select id={`doctype-${processId}`} value={type} onChange={(e) => setType(e.target.value)} className="rounded-lg border border-line bg-surface px-2.5 py-1.5 text-sm">
              {KYC_DOCUMENT_TYPES.map((t) => <option key={t} value={t}>{DOC_TYPE_LABEL[t]}</option>)}
            </select>
          </div>
          <input ref={input} type="file" accept=".pdf,.jpg,.jpeg,.png" className="sr-only" aria-label="Selecionar documento" onChange={(e) => { onFile(e.target.files?.[0]); e.target.value = '' }} />
          <Button variant="secondary" loading={upload.isPending} onClick={() => input.current?.click()}><Paperclip size={14} aria-hidden /> Enviar documento</Button>
          <span className="text-xs text-muted">PDF, JPG ou PNG, até 10 MB.</span>
        </div>
      )}
      {fileError && <p role="alert" className="mt-2 text-sm text-danger">{fileError}</p>}
    </div>
  )
}

function Process({ p }: { p: KycProcess }) {
  const { can } = useAuth()
  const approve = useApproveKyc()
  const reject = useRejectKyc()
  const [dialog, setDialog] = useState<'approve' | 'reject' | null>(null)
  const st = KYC_STATUS[p.status] ?? { label: p.status, tone: 'neutral' as Tone }
  const open = p.status === 'IN_PROGRESS'
  return (
    <Card
      title={<span className="inline-flex flex-wrap items-center gap-2">KYC {p.type} <Badge tone={st.tone}>{st.label}</Badge></span>}
      actions={
        open && can('kyc:approve') ? (
          <span className="flex gap-2">
            <Button onClick={() => setDialog('approve')}>Aprovar KYC</Button>
            <Button variant="danger" onClick={() => setDialog('reject')}>Reprovar KYC</Button>
          </span>
        ) : undefined
      }
    >
      <dl className="mb-4 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
        <div><dt className="text-xs text-muted">Início</dt><dd>{formatDate(p.startedAt ?? p.createdAt)}</dd></div>
        <div><dt className="text-xs text-muted">Conclusão</dt><dd>{formatDate(p.completedAt)}</dd></div>
        <div><dt className="text-xs text-muted">Validade</dt><dd>{formatDate(p.expiresAt)}</dd></div>
        <div><dt className="text-xs text-muted">Risco</dt><dd>{p.riskLevel ?? '—'}</dd></div>
      </dl>
      <div className="grid gap-6 lg:grid-cols-2">
        <Checklist processId={p.id} editable={open && can('kyc:write')} />
        <Documents processId={p.id} editable={open && can('kyc:write')} canApprove={open && can('kyc:approve')} />
      </div>
      <ConfirmDialog open={dialog === 'approve'} title="Aprovar KYC" description="Confirma a aprovação do processo de KYC desta parte?" confirmLabel="Aprovar" onCancel={() => setDialog(null)} onConfirm={async () => { await approve.mutateAsync(p.id); setDialog(null) }} />
      <ConfirmDialog open={dialog === 'reject'} title="Reprovar KYC" description="Confirma a reprovação do processo de KYC desta parte?" confirmLabel="Reprovar" onCancel={() => setDialog(null)} onConfirm={async () => { await reject.mutateAsync(p.id); setDialog(null) }} />
    </Card>
  )
}

export function KycTab({ partyId }: { partyId: UUID }) {
  const { can } = useAuth()
  const q = useKycProcesses(partyId)
  const start = useStartOnboarding()
  return (
    <div className="space-y-4">
      {can('kyc:write') && (
        <div className="flex justify-end">
          <Button variant="secondary" loading={start.isPending} onClick={() => start.mutate(partyId)}>Iniciar processo de KYC</Button>
        </div>
      )}
      {q.isLoading && <Skeleton className="h-24 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
      {q.data && q.data.length === 0 && <Card><EmptyState title="Nenhum processo de KYC para esta parte." /></Card>}
      {q.data?.map((p) => <Process key={p.id} p={p} />)}
    </div>
  )
}
