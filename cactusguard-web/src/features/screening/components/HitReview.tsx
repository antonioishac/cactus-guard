import { useState } from 'react'
import { Check, X } from 'lucide-react'
import type { ScreeningResult } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { JustificationDialog } from '@/components/ui/JustificationDialog'
import { useAuth } from '@/features/auth/useAuth'
import { useReviewHit } from '../hooks'
import { HIT_STATUS_LABEL, HIT_TONE } from '../hitLabels'

export const HitStatusBadge = ({ status }: { status: string }) => <Badge tone={HIT_TONE[status] ?? 'neutral'}>{HIT_STATUS_LABEL[status] ?? status}</Badge>

type Decision = 'CONFIRMED' | 'FALSE_POSITIVE'

/** Revisão de hit: justificativa obrigatória (>= 20) + confirmação. Só ANALYST/SUPERVISOR, só hits pendentes. */
export function HitReviewButtons({ hit }: { hit: ScreeningResult }) {
  const { can } = useAuth()
  const review = useReviewHit()
  const [decision, setDecision] = useState<Decision | null>(null)
  if (hit.status !== 'PENDING' || !can('screening:review')) return null

  const confirm = decision === 'CONFIRMED'
  return (
    <>
      <span className="inline-flex gap-1">
        <Button variant="secondary" className="!px-2 !py-1 text-xs" onClick={() => setDecision('CONFIRMED')} aria-label={`Confirmar hit ${hit.matchedName}`}>
          <Check size={12} aria-hidden /> Confirmar
        </Button>
        <Button variant="ghost" className="!px-2 !py-1 text-xs" onClick={() => setDecision('FALSE_POSITIVE')} aria-label={`Marcar hit ${hit.matchedName} como falso positivo`}>
          <X size={12} aria-hidden /> Falso positivo
        </Button>
      </span>
      <JustificationDialog
        open={decision !== null}
        title={confirm ? 'Confirmar hit de screening' : 'Marcar hit como falso positivo'}
        description={
          confirm
            ? `Confirmar ${hit.matchedName} na lista ${hit.listCode} eleva o risco, gera alerta e, em lista CSNU, aplica o bloqueio imediato de ativos. Não dê ciência ao cliente.`
            : 'Descarta o hit como homônimo/coincidência. A fundamentação fica registrada.'
        }
        confirmLabel={confirm ? 'Confirmar hit' : 'Marcar falso positivo'}
        tone={confirm ? 'danger' : 'primary'}
        onCancel={() => setDecision(null)}
        onConfirm={async ({ justification }) => {
          await review.mutateAsync({ id: hit.id, status: decision!, justification })
          setDecision(null)
        }}
      />
    </>
  )
}
