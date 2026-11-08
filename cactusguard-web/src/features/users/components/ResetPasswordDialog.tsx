import { useState } from 'react'
import { Copy, KeyRound, TriangleAlert } from 'lucide-react'
import { errorMessage } from '@/api/errors'
import type { UserAccount } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { toast } from '@/stores/toastStore'
import { useResetPassword } from '../hooks'

type Mode = 'generate' | 'set'
const MIN = 10
const MAX = 128

/**
 * Redefinição de senha (ADMIN). Sem senha informada o servidor gera uma e a devolve UMA vez: ela fica só no
 * estado deste componente, é exibida com aviso e botão de cópia e descartada ao fechar. Nunca vai a storage, URL ou log.
 */
export function ResetPasswordDialog({ user, onClose }: { user: UserAccount; onClose: () => void }) {
  const reset = useResetPassword()
  const [mode, setMode] = useState<Mode>('generate')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [temp, setTemp] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  function close() {
    setTemp(null)
    setPassword('')
    reset.reset()
    onClose()
  }

  async function submit() {
    setError(null)
    if (mode === 'set' && (password.length < MIN || password.length > MAX)) {
      setError(`A senha deve ter entre ${MIN} e ${MAX} caracteres.`)
      return
    }
    try {
      const res = await reset.mutateAsync({ id: user.id, newPassword: mode === 'set' ? password : undefined })
      setPassword('')
      setTemp(res.temporaryPassword)
      setDone(true)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  async function copy() {
    if (!temp) return
    try {
      await navigator.clipboard.writeText(temp)
      toast.info('Senha copiada. Ela não será exibida novamente.')
    } catch {
      toast.error('Não foi possível copiar. Selecione e copie manualmente.')
    }
  }

  return (
    <Modal open title={`Redefinir senha de ${user.name}`} onClose={close}>
      {!done ? (
        <div className="space-y-3">
          <p className="text-sm text-muted">Todas as sessões do usuário serão encerradas (refresh tokens revogados). A ação fica registrada na auditoria, sem a senha.</p>
          <fieldset className="space-y-1.5">
            <legend className="sr-only">Modo</legend>
            <label className="flex items-start gap-2 text-sm">
              <input type="radio" name="reset-mode" checked={mode === 'generate'} onChange={() => setMode('generate')} className="mt-0.5" />
              <span><strong>Gerar senha temporária</strong> <span className="text-muted">(exibida uma única vez)</span></span>
            </label>
            <label className="flex items-start gap-2 text-sm">
              <input type="radio" name="reset-mode" checked={mode === 'set'} onChange={() => setMode('set')} className="mt-0.5" />
              <span><strong>Definir uma nova senha</strong></span>
            </label>
          </fieldset>
          {mode === 'set' && (
            <div>
              <label htmlFor="reset-pass" className="mb-1 block text-sm font-medium">Nova senha ({MIN} a {MAX} caracteres)</label>
              <input id="reset-pass" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} className="w-full rounded-lg border border-line px-3 py-2 text-sm" />
            </div>
          )}
          {error && <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={close} disabled={reset.isPending}>Cancelar</Button>
            <Button onClick={() => void submit()} loading={reset.isPending}><KeyRound size={14} aria-hidden /> Redefinir senha</Button>
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          {temp ? (
            <>
              <div role="alert" className="flex items-start gap-2 rounded-lg border border-warning bg-warning-bg p-3 text-sm text-warning">
                <TriangleAlert size={16} className="mt-0.5 shrink-0" aria-hidden />
                <p><strong>Esta senha é exibida apenas uma vez.</strong> Copie agora e repasse ao usuário por canal seguro. Ela não é guardada pelo sistema.</p>
              </div>
              <div className="flex items-center gap-2">
                <output aria-label="Senha temporária" className="flex-1 select-all rounded-lg border border-line bg-canvas px-3 py-2 font-mono text-sm">{temp}</output>
                <Button variant="secondary" onClick={() => void copy()} aria-label="Copiar senha temporária"><Copy size={14} aria-hidden /> Copiar</Button>
              </div>
            </>
          ) : (
            <p className="text-sm">Senha redefinida. O usuário deve entrar com a senha definida por você.</p>
          )}
          <div className="flex justify-end">
            <Button onClick={close}>Concluir</Button>
          </div>
        </div>
      )}
    </Modal>
  )
}
