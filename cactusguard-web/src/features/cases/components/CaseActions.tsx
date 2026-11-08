import { useState } from 'react'
import { ArrowUpRight, Archive, BadgeCheck, Megaphone } from 'lucide-react'
import type { Case, CaseDecision } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { JustificationDialog } from '@/components/ui/JustificationDialog'
import { useAuth } from '@/features/auth/useAuth'
import { useApproveCase, useDecideCase, useEscalateCase } from '../hooks'
import { isActionable, isOwnDecision, isPendingApproval } from '../ui-rules'

type Dialog = { kind: 'decide'; decision: CaseDecision } | { kind: 'escalate' } | { kind: 'approve' } | null

const DECISION_COPY: Record<CaseDecision, { title: string; description: string; confirm: string }> = {
  COMMUNICATE: {
    title: 'Decidir: comunicar ao COAF',
    description: 'Decisão regulatória. Se aprovada, gera a comunicação ao SISCOAF com prazo até o dia útil seguinte. Não dê ciência ao cliente (Lei 9.613/98, art. 11, II).',
    confirm: 'Registrar decisão: Comunicar',
  },
  ARCHIVE: {
    title: 'Decidir: arquivar',
    description: 'Decisão regulatória de não comunicar. A fundamentação ficará registrada no dossiê.',
    confirm: 'Registrar decisão: Arquivar',
  },
}

/** Botões de ação do case conforme role e status. O backend valida tudo de novo. */
export function CaseActions({ c }: { c: Case }) {
  const { can, user } = useAuth()
  const [dialog, setDialog] = useState<Dialog>(null)
  const decide = useDecideCase()
  const escalate = useEscalateCase()
  const approve = useApproveCase()

  const actionable = isActionable(c)
  const pending = isPendingApproval(c)
  const own = isOwnDecision(c, user)

  return (
    <>
      <div className="flex flex-wrap items-center gap-2">
        {can('case:decide') && (
          <>
            <Button disabled={!actionable} onClick={() => setDialog({ kind: 'decide', decision: 'COMMUNICATE' })}>
              <Megaphone size={16} aria-hidden /> Comunicar
            </Button>
            <Button variant="secondary" disabled={!actionable} onClick={() => setDialog({ kind: 'decide', decision: 'ARCHIVE' })}>
              <Archive size={16} aria-hidden /> Arquivar
            </Button>
          </>
        )}
        {can('case:escalate') && (
          <Button variant="secondary" disabled={!actionable} onClick={() => setDialog({ kind: 'escalate' })}>
            <ArrowUpRight size={16} aria-hidden /> Escalar
          </Button>
        )}
        {can('case:approve') && pending && !own && (
          <Button onClick={() => setDialog({ kind: 'approve' })}>
            <BadgeCheck size={16} aria-hidden /> Aprovar decisão
          </Button>
        )}
      </div>
      {can('case:approve') && pending && own && (
        <p role="note" className="mt-2 text-sm text-warning">
          Você propôs esta decisão. A aprovação deve ser feita por outro supervisor (quatro olhos).
        </p>
      )}

      {dialog?.kind === 'decide' && (
        <JustificationDialog
          open
          {...DECISION_COPY[dialog.decision]}
          confirmLabel={DECISION_COPY[dialog.decision].confirm}
          tone={dialog.decision === 'COMMUNICATE' ? 'danger' : 'primary'}
          onCancel={() => setDialog(null)}
          onConfirm={async ({ justification }) => {
            await decide.mutateAsync({ id: c.id, decision: dialog.decision, justification })
            setDialog(null)
          }}
        />
      )}
      <JustificationDialog
        open={dialog?.kind === 'escalate'}
        title="Escalar case"
        description="Transição de workflow para nível superior. Não encerra o case nem interrompe os prazos."
        confirmLabel="Escalar"
        onCancel={() => setDialog(null)}
        onConfirm={async ({ justification }) => {
          await escalate.mutateAsync({ id: c.id, justification })
          setDialog(null)
        }}
      />
      <JustificationDialog
        open={dialog?.kind === 'approve'}
        title="Aprovar decisão (quatro olhos)"
        description={`Decisão proposta: ${c.decision === 'COMMUNICATE' ? 'Comunicar' : 'Arquivar'}. Registre a fundamentação da sua aprovação.`}
        confirmLabel="Aprovar"
        onCancel={() => setDialog(null)}
        onConfirm={async ({ justification }) => {
          await approve.mutateAsync({ id: c.id, justification })
          setDialog(null)
        }}
      />
    </>
  )
}
