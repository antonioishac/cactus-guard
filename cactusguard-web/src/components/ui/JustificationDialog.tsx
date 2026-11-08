import { useId } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Modal } from './Modal'
import { Button } from './Button'
import { errorMessage } from '@/api/errors'

const DEFAULT_MIN_JUSTIFICATION = 20

/** Conta caracteres não brancos, como o backend (Justifications.require). */
const nonBlankLength = (s: string) => s.replace(/\s/g, '').length

function buildJustificationSchema(minLength: number, withReason: boolean, maxLength?: number) {
  return z.object({
    justification: z
      .string()
      .refine((v) => v.trim().length > 0, 'A justificativa é obrigatória.')
      .refine((v) => v.trim().length === 0 || nonBlankLength(v) >= minLength, `Informe ao menos ${minLength} caracteres.`)
      .refine((v) => maxLength === undefined || v.trim().length <= maxLength, `Máximo de ${maxLength} caracteres.`),
    reason: withReason
      ? z.string().trim().min(1, 'O motivo é obrigatório.').max(100, 'Máximo de 100 caracteres.')
      : z.string().optional(),
    confirm: z.boolean().refine((v) => v === true, 'Confirme para prosseguir.'),
  })
}

export interface JustificationValues {
  justification: string
  reason?: string
}

interface Props {
  open: boolean
  title: string
  description?: string
  confirmLabel: string
  /** mínimo de caracteres não brancos (padrão 20) */
  minLength?: number
  /** máximo de caracteres da justificativa (sem limite por padrão) */
  maxLength?: number
  /** pede também um motivo curto (ex.: falso positivo) */
  reasonLabel?: string
  tone?: 'primary' | 'danger'
  onCancel: () => void
  onConfirm: (values: JustificationValues) => Promise<unknown>
}

/**
 * Modal de confirmação de ações regulatórias: justificativa obrigatória + checkbox de confirmação explícita.
 * Texto digitado nunca é logado.
 */
export function JustificationDialog({
  open,
  title,
  description,
  confirmLabel,
  minLength = DEFAULT_MIN_JUSTIFICATION,
  maxLength,
  reasonLabel,
  tone = 'primary',
  onCancel,
  onConfirm,
}: Props) {
  const uid = useId()
  const withReason = !!reasonLabel
  const schema = buildJustificationSchema(minLength, withReason, maxLength)
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { justification: '', reason: '', confirm: false },
  })

  function close() {
    reset()
    onCancel()
  }

  const submit = handleSubmit(async (v) => {
    try {
      await onConfirm({ justification: v.justification.trim(), reason: v.reason?.trim() })
      reset()
    } catch (e) {
      setError('root', { message: errorMessage(e) })
    }
  })

  return (
    <Modal open={open} title={title} onClose={close}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {description && <p className="text-sm text-muted">{description}</p>}

        {withReason && (
          <div>
            <label htmlFor={`${uid}-reason`} className="mb-1 block text-sm font-medium">
              {reasonLabel}
            </label>
            <input
              id={`${uid}-reason`}
              {...register('reason')}
              maxLength={100}
              aria-invalid={!!errors.reason}
              aria-describedby={errors.reason ? `${uid}-reason-err` : undefined}
              className="w-full rounded-lg border border-line px-3 py-2 text-sm"
            />
            {errors.reason && (
              <p id={`${uid}-reason-err`} role="alert" className="mt-1 text-xs text-danger">
                {errors.reason.message}
              </p>
            )}
          </div>
        )}

        <div>
          <label htmlFor={`${uid}-just`} className="mb-1 block text-sm font-medium">
            Justificativa (obrigatória, mín. {minLength} caracteres)
          </label>
          <textarea
            id={`${uid}-just`}
            rows={4}
            maxLength={maxLength}
            {...register('justification')}
            aria-invalid={!!errors.justification}
            aria-describedby={errors.justification ? `${uid}-just-err` : undefined}
            className="w-full rounded-lg border border-line px-3 py-2 text-sm"
          />
          {errors.justification && (
            <p id={`${uid}-just-err`} role="alert" className="mt-1 text-xs text-danger">
              {errors.justification.message}
            </p>
          )}
        </div>

        <div>
          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" {...register('confirm')} className="mt-0.5 h-4 w-4" />
            <span>Confirmo que revisei as informações e que esta ação ficará registrada na trilha de auditoria.</span>
          </label>
          {errors.confirm && (
            <p role="alert" className="mt-1 text-xs text-danger">
              {errors.confirm.message}
            </p>
          )}
        </div>

        {errors.root && (
          <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">
            {errors.root.message}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={close} disabled={isSubmitting}>
            Cancelar
          </Button>
          <Button type="submit" variant={tone === 'danger' ? 'danger' : 'primary'} loading={isSubmitting}>
            {confirmLabel}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
