import { useState } from 'react'
import { Modal } from './Modal'
import { Button } from './Button'
import { errorMessage } from '@/api/errors'

interface Props {
  open: boolean
  title: string
  description: string
  confirmLabel: string
  onCancel: () => void
  onConfirm: () => Promise<unknown>
}

/** Confirmação explícita simples (ações sem justificativa, ex.: abrir case). */
export function ConfirmDialog({ open, title, description, confirmLabel, onCancel, onConfirm }: Props) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function go() {
    setBusy(true)
    setError(null)
    try {
      await onConfirm()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} title={title} onClose={onCancel}>
      <p className="mb-4 text-sm text-muted">{description}</p>
      {error && (
        <p role="alert" className="mb-3 rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">
          {error}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel} disabled={busy}>
          Cancelar
        </Button>
        <Button onClick={() => void go()} loading={busy}>
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  )
}
