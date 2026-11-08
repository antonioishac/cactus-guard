import type { ReactNode } from 'react'
import { AlertTriangle, Inbox } from 'lucide-react'
import { toApiError } from '@/api/errors'
import { Button } from './Button'

export function Skeleton({ className = '' }: { className?: string }) {
  return <div aria-hidden className={`animate-pulse rounded bg-line ${className}`} />
}

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 px-4 py-10 text-center">
      <Inbox size={28} className="text-muted" aria-hidden />
      <p className="font-medium">{title}</p>
      {hint && <p className="max-w-md text-sm text-muted">{hint}</p>}
      {action}
    </div>
  )
}

/**
 * Estado de erro de uma seção. `unavailableOn` lista status HTTP que significam "endpoint ainda não existe /
 * sem permissão" e viram um aviso discreto em vez de erro.
 */
export function ErrorState({ error, onRetry, compact }: { error: unknown; onRetry?: () => void; compact?: boolean }) {
  const e = toApiError(error)
  const unavailable = e.status === 404 || e.status === 501
  const forbidden = e.status === 403
  const msg = unavailable
    ? 'Informação ainda não disponível nesta versão da API.'
    : forbidden
      ? 'Seu perfil não tem permissão para ver esta seção.'
      : e.message
  return (
    <div role={unavailable || forbidden ? 'status' : 'alert'} className={`flex items-start gap-2 text-sm ${compact ? 'py-2' : 'py-6 justify-center'} ${unavailable || forbidden ? 'text-muted' : 'text-danger'}`}>
      <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
      <div>
        <p>{msg}</p>
        {e.traceId && !unavailable && <p className="text-xs text-muted">Ref.: {e.traceId}</p>}
        {onRetry && !unavailable && !forbidden && (
          <Button variant="secondary" className="mt-2 !px-2 !py-1 text-xs" onClick={onRetry}>
            Tentar novamente
          </Button>
        )}
      </div>
    </div>
  )
}
