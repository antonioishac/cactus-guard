import { useId, useState } from 'react'
import { Link } from 'react-router'
import { AlertTriangle } from 'lucide-react'
import { errorMessage } from '@/api/errors'
import type { SiscoafCommunication } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Card, PageHeader } from '@/components/ui/Layout'
import { useAuth } from '@/features/auth/useAuth'
import { SecrecyNotice, SiscoafStatusBadge } from '../components/Shared'
import { useNegativeDeclaration } from '../hooks'

export function NegativeDeclarationPage() {
  const { can } = useAuth()
  const uid = useId()
  const current = new Date().getFullYear()
  const [year, setYear] = useState(current - 1)
  const [confirm, setConfirm] = useState(false)
  const [blocked, setBlocked] = useState<string | null>(null)
  const [created, setCreated] = useState<SiscoafCommunication | null>(null)
  const gen = useNegativeDeclaration()
  const years = Array.from({ length: 6 }, (_, i) => current - i)

  return (
    <>
      <PageHeader title="Declaração negativa" subtitle="Comunicação anual de inexistência de operações suspeitas ou em espécie comunicáveis" />
      <SecrecyNotice />
      <Card title="Gerar por ano de referência">
        <p className="mb-4 text-sm text-muted">
          A declaração negativa só pode ser gerada se <strong>não houve</strong> COS/COE enviadas no ano de referência. A validação é do backend; o motivo do bloqueio é exibido abaixo.
        </p>
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label htmlFor={uid} className="mb-1 block text-xs font-medium text-muted">Ano de referência</label>
            <select id={uid} value={year} onChange={(e) => setYear(Number(e.target.value))} className="rounded-lg border border-line bg-surface px-2.5 py-1.5 text-sm">
              {years.map((y) => (
                <option key={y} value={y}>{y}</option>
              ))}
            </select>
          </div>
          {can('siscoaf:act') ? (
            <Button
              onClick={() => {
                setBlocked(null)
                setCreated(null)
                setConfirm(true)
              }}
            >
              Gerar declaração negativa
            </Button>
          ) : (
            <p className="text-sm text-muted">Somente supervisores podem gerar a declaração.</p>
          )}
        </div>

        {blocked && (
          <div role="alert" className="mt-4 flex items-start gap-2 rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">
            <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
            <p>
              <strong>Geração bloqueada:</strong> {blocked}
            </p>
          </div>
        )}
        {created && (
          <div role="status" className="mt-4 rounded-lg bg-success-bg px-3 py-2 text-sm text-success">
            Declaração negativa de {created.referenceYear} gerada como <SiscoafStatusBadge status={created.status} />.{' '}
            <Link to={`/siscoaf/${created.id}`} className="underline">
              Abrir para aprovar e enviar
            </Link>
          </div>
        )}
      </Card>

      <ConfirmDialog
        open={confirm}
        title={`Gerar declaração negativa de ${year}`}
        description="Será criado um rascunho. O envio ao COAF continua exigindo aprovação e confirmação explícitas na tela da comunicação."
        confirmLabel="Gerar"
        onCancel={() => setConfirm(false)}
        onConfirm={async () => {
          try {
            setCreated(await gen.mutateAsync(year))
          } catch (e) {
            setBlocked(errorMessage(e))
          } finally {
            setConfirm(false)
          }
        }}
      />
    </>
  )
}
