import { useEffect, useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { revealPartyField } from '@/api/parties'
import type { PiiField, UUID } from '@/api/types'
import { useAuthStore } from '@/stores/authStore'
import { can } from '@/lib/permissions'
import { JustificationDialog } from './JustificationDialog'
import { toast } from '@/stores/toastStore'

const REVEAL_MS = 30_000
const REVEAL_MIN_REASON = 10

interface Props {
  /** valor já mascarado, como veio da API */
  value: string | null | undefined
  partyId?: UUID
  field?: PiiField
  label: string
}

/**
 * Exibe PII mascarada. "Revelar" pede motivo, chama o backend (que registra o acesso no DataAccessLog) e
 * mostra o valor só em memória do componente por 30 s. O valor revelado nunca vai para URL, storage ou log.
 */
export function MaskedValue({ value, partyId, field, label }: Props) {
  const roles = useAuthStore((s) => s.user?.roles ?? [])
  const [revealed, setRevealed] = useState<string | null>(null)
  const [asking, setAsking] = useState(false)
  const canReveal = !!partyId && !!field && !!value && can(roles, 'pii:reveal')

  useEffect(() => {
    if (!revealed) return
    const t = setTimeout(() => setRevealed(null), REVEAL_MS)
    return () => clearTimeout(t)
  }, [revealed])

  if (!value) return <span>—</span>

  return (
    <span className="inline-flex items-center gap-1.5">
      <span className="font-mono text-sm" data-testid="masked-value">{revealed ?? value}</span>
      {canReveal &&
        (revealed ? (
          <button onClick={() => setRevealed(null)} aria-label={`Ocultar ${label}`} className="rounded p-1 text-muted hover:bg-neutral-bg">
            <EyeOff size={14} />
          </button>
        ) : (
          <button onClick={() => setAsking(true)} aria-label={`Revelar ${label}`} className="rounded p-1 text-muted hover:bg-neutral-bg">
            <Eye size={14} />
          </button>
        ))}
      {canReveal && (
        <JustificationDialog
          open={asking}
          title={`Revelar ${label}`}
          description="O acesso ao dado completo é registrado (LGPD). Informe o motivo."
          confirmLabel="Revelar"
          minLength={REVEAL_MIN_REASON}
          onCancel={() => setAsking(false)}
          onConfirm={async ({ justification }) => {
            const res = await revealPartyField(partyId, { field, reason: justification })
            setRevealed(res.value)
            setAsking(false)
            toast.info('Acesso registrado. O dado será ocultado em 30 s.')
          }}
        />
      )}
    </span>
  )
}
