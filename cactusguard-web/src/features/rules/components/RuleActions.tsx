import { useState } from 'react'
import { Link } from 'react-router'
import type { MonitoringRule } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { JustificationDialog } from '@/components/ui/JustificationDialog'
import { useAuth } from '@/features/auth/useAuth'
import { useApproveRule, useRunEngine, useToggleRule } from '../hooks'
import { canDeactivate, canRequestActivation, isOwnRule, isPendingRule } from '../ui-rules'

type Dialog = 'approve' | 'deactivate' | 'activate' | null

export function RuleActions({ rule }: { rule: MonitoringRule }) {
  const { can, user } = useAuth()
  const [dialog, setDialog] = useState<Dialog>(null)
  const approve = useApproveRule()
  const toggle = useToggleRule()
  const close = () => setDialog(null)

  if (!can('rule:write') && !can('rule:approve')) {
    return <p className="text-sm text-muted">Seu perfil tem acesso somente leitura às regras.</p>
  }

  const own = isOwnRule(rule, user)
  const showApprove = can('rule:approve') && isPendingRule(rule)

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {can('rule:write') && (
          <Link to={`/regras/${encodeURIComponent(rule.code)}/editar`}>
            <Button variant="secondary">Editar (nova versão)</Button>
          </Link>
        )}
        {can('rule:write') && canRequestActivation(rule) && <Button onClick={() => setDialog('activate')}>Enviar para aprovação / ativar</Button>}
        {can('rule:write') && canDeactivate(rule) && <Button variant="danger" onClick={() => setDialog('deactivate')}>Desativar</Button>}
        {showApprove && (
          <Button onClick={() => setDialog('approve')} disabled={own} aria-describedby={own ? 'four-eyes-hint' : undefined}>
            Aprovar regra
          </Button>
        )}
      </div>
      {showApprove && own && (
        <p id="four-eyes-hint" className="text-sm text-warning">
          Quatro olhos: você criou ou alterou esta versão, então outro supervisor precisa aprová-la.
        </p>
      )}
      {showApprove && !own && !rule.createdById && (
        <p className="text-xs text-muted">A aprovação só é aceita de um supervisor diferente de quem criou/alterou a regra; o servidor valida.</p>
      )}
      {isPendingRule(rule) && !can('rule:approve') && <p className="text-sm text-muted">Aguardando aprovação de um supervisor.</p>}

      <JustificationDialog
        open={dialog === 'approve'}
        title={`Aprovar ${rule.code} (v${rule.version})`}
        description="A regra passa a ser usada pelo motor com os parâmetros desta versão."
        confirmLabel="Aprovar"
        onCancel={close}
        onConfirm={async ({ justification }) => {
          await approve.mutateAsync({ code: rule.code, justification })
          close()
        }}
      />
      <JustificationDialog
        open={dialog === 'deactivate'}
        title={`Desativar ${rule.code}`}
        description="O motor deixa de avaliar esta regra imediatamente."
        confirmLabel="Desativar"
        tone="danger"
        onCancel={close}
        onConfirm={async ({ justification }) => {
          await toggle.mutateAsync({ code: rule.code, active: false, justification })
          close()
        }}
      />
      <JustificationDialog
        open={dialog === 'activate'}
        title={`Solicitar ativação de ${rule.code}`}
        description="A regra vai para aprovação e só passa a valer após aprovação de outro supervisor."
        confirmLabel="Enviar para aprovação"
        onCancel={close}
        onConfirm={async ({ justification }) => {
          await toggle.mutateAsync({ code: rule.code, active: true, justification })
          close()
        }}
      />
    </div>
  )
}

/** Execução manual do motor (SUPERVISOR/ADMIN). */
export function RunEngineButton() {
  const { can } = useAuth()
  const run = useRunEngine()
  const [open, setOpen] = useState(false)
  const [result, setResult] = useState<string | null>(null)
  if (!can('rule:run')) return null
  return (
    <>
      <Button variant="secondary" onClick={() => setOpen(true)}>Executar motor</Button>
      {result && (
        <span role="status" className="self-center text-sm text-success">
          {result}
        </span>
      )}
      <ConfirmDialog
        open={open}
        title="Executar o motor de regras"
        description="Avalia agora todas as regras ativas e pode gerar novos alertas. Use apenas quando necessário."
        confirmLabel="Executar"
        onCancel={() => setOpen(false)}
        onConfirm={async () => {
          const r = await run.mutateAsync()
          setResult(`Motor executado: ${r.alertsGenerated} alerta(s) gerado(s).`)
          setOpen(false)
        }}
      />
    </>
  )
}
