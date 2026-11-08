import { useState } from 'react'
import { BadgeCheck, ShieldAlert } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { errorMessage } from '@/api/errors'
import { PEP_CATEGORIES } from '@/api/types'
import type { Party } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { JustificationDialog } from '@/components/ui/JustificationDialog'
import { Card, KeyValue } from '@/components/ui/Layout'
import { Modal } from '@/components/ui/Modal'
import { useAuth } from '@/features/auth/useAuth'
import { formatDate, formatDateTime } from '@/lib/format'
import { useApprovePep, useMarkPep } from '../../hooks'
import { isPepActive, PEP_CATEGORY_LABEL, PEP_STATUS_LABEL } from '../../rules'

const markSchema = z.object({
  category: z.enum(PEP_CATEGORIES),
  role: z.string().trim().min(1, 'Informe o cargo ou função.'),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  related: z.boolean(),
  justification: z.string().refine((v) => v.replace(/\s/g, '').length >= 20, 'Informe ao menos 20 caracteres.'),
})
type MarkValues = z.infer<typeof markSchema>

const CONTROL = 'w-full rounded-lg border border-line px-3 py-2 text-sm'

function MarkPepDialog({ party, onClose }: { party: Party; onClose: () => void }) {
  const mark = useMarkPep(party.id)
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<MarkValues>({
    resolver: zodResolver(markSchema),
    defaultValues: { category: 'NATIONAL', role: party.pepRole ?? '', startDate: party.pepStartDate ?? '', endDate: party.pepEndDate ?? '', related: party.pepStatus === 'RELATED', justification: '' },
  })
  const submit = handleSubmit(async (v) => {
    try {
      await mark.mutateAsync({ category: v.category, role: v.role, startDate: v.startDate || null, endDate: v.endDate || null, related: v.related, justification: v.justification.trim() })
      onClose()
    } catch (e) {
      setError('root', { message: errorMessage(e) })
    }
  })
  const err = (m?: string) => m && <p role="alert" className="mt-1 text-xs text-danger">{m}</p>
  return (
    <Modal open title="Marcar / atualizar condição de PEP" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-3">
        <p className="text-sm text-muted">Força EDD e risco mínimo Alto. A aprovação da alta administração é uma etapa separada.</p>
        <div>
          <label htmlFor="pep-cat" className="mb-1 block text-sm font-medium">Categoria</label>
          <select id="pep-cat" {...register('category')} className={CONTROL}>
            {PEP_CATEGORIES.map((c) => <option key={c} value={c}>{PEP_CATEGORY_LABEL[c]}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="pep-role" className="mb-1 block text-sm font-medium">Cargo / função</label>
          <input id="pep-role" {...register('role')} className={CONTROL} />
          {err(errors.role?.message)}
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label htmlFor="pep-start" className="mb-1 block text-sm font-medium">Início no cargo</label>
            <input id="pep-start" type="date" {...register('startDate')} className={CONTROL} />
          </div>
          <div>
            <label htmlFor="pep-end" className="mb-1 block text-sm font-medium">Fim no cargo</label>
            <input id="pep-end" type="date" {...register('endDate')} className={CONTROL} />
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" {...register('related')} className="h-4 w-4" /> Familiar ou estreito colaborador de PEP</label>
        <div>
          <label htmlFor="pep-just" className="mb-1 block text-sm font-medium">Justificativa (obrigatória, mín. 20 caracteres)</label>
          <textarea id="pep-just" rows={3} {...register('justification')} className={CONTROL} />
          {err(errors.justification?.message)}
        </div>
        {errors.root && <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">{errors.root.message}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>Cancelar</Button>
          <Button type="submit" loading={isSubmitting}>Registrar</Button>
        </div>
      </form>
    </Modal>
  )
}

export function PepTab({ party }: { party: Party }) {
  const { can } = useAuth()
  const approve = useApprovePep(party.id)
  const [dialog, setDialog] = useState<'mark' | 'approve' | null>(null)
  const pep = isPepActive(party)
  const approved = !!party.pepApprovalAt

  return (
    <Card
      title="Pessoa exposta politicamente (PEP)"
      actions={can('pep:mark') ? <Button variant="secondary" onClick={() => setDialog('mark')}><ShieldAlert size={14} aria-hidden /> {pep ? 'Atualizar PEP' : 'Marcar como PEP'}</Button> : undefined}
    >
      {!pep ? (
        <p className="text-sm text-muted">Sem indicação de PEP{party.pepStatus === 'EXPIRED' ? ' (status expirado)' : ''}.</p>
      ) : (
        <div className="space-y-3">
          <div className="rounded-lg border border-danger bg-danger-bg p-3">
            <p className="mb-2 text-sm font-semibold text-danger">{PEP_STATUS_LABEL[party.pepStatus ?? 'ACTIVE'] ?? party.pepStatus}</p>
            <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <KeyValue label="Categoria">{party.pepCategory ? (PEP_CATEGORY_LABEL[party.pepCategory] ?? party.pepCategory) : '—'}</KeyValue>
              <KeyValue label="Cargo / função">{party.pepRole ?? '—'}</KeyValue>
              <KeyValue label="Início no cargo">{formatDate(party.pepStartDate)}</KeyValue>
              <KeyValue label="Fim no cargo">{formatDate(party.pepEndDate)}</KeyValue>
              <KeyValue label="Expira em (fim + 5 anos)">{formatDate(party.pepExpiryDate)}</KeyValue>
            </dl>
          </div>
          <div className="flex flex-wrap items-center gap-3 rounded-lg border border-line p-3 text-sm">
            <span className="font-medium">Aprovação da alta administração:</span>
            {approved ? (
              <Badge tone="success">Aprovada em {formatDateTime(party.pepApprovalAt)}{party.pepApprovalByName ? ` por ${party.pepApprovalByName}` : ''}</Badge>
            ) : (
              <Badge tone="warning">Pendente</Badge>
            )}
            {!approved && can('pep:approve') && (
              <Button onClick={() => setDialog('approve')}><BadgeCheck size={14} aria-hidden /> Aprovar relacionamento</Button>
            )}
            {!approved && !can('pep:approve') && <span className="text-xs text-muted">Somente Supervisor pode aprovar.</span>}
          </div>
        </div>
      )}

      {dialog === 'mark' && <MarkPepDialog party={party} onClose={() => setDialog(null)} />}
      <JustificationDialog
        open={dialog === 'approve'}
        title="Aprovar relacionamento com PEP"
        description="Aprovação da alta administração para iniciar ou manter o relacionamento. Registre a fundamentação."
        confirmLabel="Aprovar"
        onCancel={() => setDialog(null)}
        onConfirm={async ({ justification }) => {
          await approve.mutateAsync(justification)
          setDialog(null)
        }}
      />
    </Card>
  )
}
