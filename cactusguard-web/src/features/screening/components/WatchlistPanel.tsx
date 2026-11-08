import { useState } from 'react'
import { Plus } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { errorMessage } from '@/api/errors'
import type { WatchlistEntry } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card } from '@/components/ui/Layout'
import { Modal } from '@/components/ui/Modal'
import { MiniTable } from '@/features/parties/components/PartySections'
import { useAuth } from '@/features/auth/useAuth'
import { isValidCnpj, isValidCpf, onlyDigits } from '@/lib/document'
import { formatDate, maskDocument } from '@/lib/format'
import { severityLabel } from '@/lib/format'
import { useAddWatchlist, useDeactivateWatchlist, useWatchlist } from '../hooks'

const schema = z.object({
  document: z.string().refine((v) => isValidCpf(v) || isValidCnpj(v), 'Informe um CPF ou CNPJ válido.'),
  name: z.string().trim().min(3, 'Informe o nome.'),
  reason: z.string().refine((v) => v.replace(/\s/g, '').length >= 20, 'Motivo obrigatório (mín. 20 caracteres).'),
  severity: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']),
  confirm: z.boolean().refine((v) => v, 'Confirme para prosseguir.'),
})
type Values = z.infer<typeof schema>
const CONTROL = 'w-full rounded-lg border border-line px-3 py-2 text-sm'

function AddDialog({ onClose }: { onClose: () => void }) {
  const add = useAddWatchlist()
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { document: '', name: '', reason: '', severity: 'HIGH', confirm: false },
  })
  const submit = handleSubmit(async (v) => {
    try {
      await add.mutateAsync({ document: onlyDigits(v.document), name: v.name.trim(), reason: v.reason.trim(), severity: v.severity })
      onClose()
    } catch (e) {
      setError('root', { message: errorMessage(e) })
    }
  })
  const err = (m?: string) => m && <p role="alert" className="mt-1 text-xs text-danger">{m}</p>
  return (
    <Modal open title="Incluir na lista interna" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-3">
        <div>
          <label htmlFor="wl-doc" className="mb-1 block text-sm font-medium">CPF ou CNPJ</label>
          <input id="wl-doc" autoComplete="off" {...register('document')} className={CONTROL} />
          {err(errors.document?.message)}
        </div>
        <div>
          <label htmlFor="wl-name" className="mb-1 block text-sm font-medium">Nome</label>
          <input id="wl-name" {...register('name')} className={CONTROL} />
          {err(errors.name?.message)}
        </div>
        <div>
          <label htmlFor="wl-sev" className="mb-1 block text-sm font-medium">Severidade</label>
          <select id="wl-sev" {...register('severity')} className={CONTROL}>
            {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((s) => <option key={s} value={s}>{severityLabel(s)}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="wl-reason" className="mb-1 block text-sm font-medium">Justificativa (obrigatória, mín. 20 caracteres)</label>
          <textarea id="wl-reason" rows={3} {...register('reason')} className={CONTROL} />
          {err(errors.reason?.message)}
        </div>
        <label className="flex items-start gap-2 text-sm"><input type="checkbox" {...register('confirm')} className="mt-0.5 h-4 w-4" /> Confirmo a inclusão; ela ficará registrada na trilha de auditoria.</label>
        {err(errors.confirm?.message)}
        {errors.root && <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">{errors.root.message}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>Cancelar</Button>
          <Button type="submit" loading={isSubmitting}>Incluir</Button>
        </div>
      </form>
    </Modal>
  )
}

export function WatchlistPanel() {
  const { can } = useAuth()
  const q = useWatchlist()
  const deactivate = useDeactivateWatchlist()
  const [adding, setAdding] = useState(false)
  const [removing, setRemoving] = useState<WatchlistEntry | null>(null)
  const write = can('watchlist:write')

  return (
    <Card title="Lista interna (watchlist)" actions={write ? <Button variant="secondary" onClick={() => setAdding(true)}><Plus size={14} aria-hidden /> Incluir</Button> : undefined}>
      {!write && <p className="mb-3 text-xs text-muted">Inclusão e desativação são restritas a Supervisor.</p>}
      {q.isLoading && <Skeleton className="h-16 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} compact />}
      {q.data && q.data.length === 0 && <EmptyState title="Nenhum registro ativo na lista interna." />}
      {q.data && q.data.length > 0 && (
        <MiniTable head={['Nome', 'Documento', 'Severidade', 'Motivo', 'Incluído em', 'Expira em', 'Ações']}>
          {q.data.map((w) => (
            <tr key={w.id}>
              <td className="px-2 py-1.5">{w.name}</td>
              <td className="px-2 py-1.5 font-mono">{maskDocument(w.document)}</td>
              <td className="px-2 py-1.5"><Badge tone={w.severity === 'CRITICAL' || w.severity === 'HIGH' ? 'danger' : 'warning'}>{severityLabel(w.severity)}</Badge></td>
              <td className="px-2 py-1.5">{w.reason}</td>
              <td className="px-2 py-1.5">{formatDate(w.createdAt)}</td>
              <td className="px-2 py-1.5">{formatDate(w.expiresAt)}</td>
              <td className="px-2 py-1.5">{write && <Button variant="ghost" className="!px-2 !py-1 text-xs" onClick={() => setRemoving(w)} aria-label={`Desativar ${w.name}`}>Desativar</Button>}</td>
            </tr>
          ))}
        </MiniTable>
      )}
      {adding && <AddDialog onClose={() => setAdding(false)} />}
      <ConfirmDialog
        open={!!removing}
        title="Desativar registro da lista interna"
        description={`Deseja desativar ${removing?.name ?? ''}? Ele deixa de gerar hits em novos screenings.`}
        confirmLabel="Desativar"
        onCancel={() => setRemoving(null)}
        onConfirm={async () => {
          await deactivate.mutateAsync(removing!.id)
          setRemoving(null)
        }}
      />
    </Card>
  )
}
