import { CheckCircle2, Info, X, XCircle } from 'lucide-react'
import { useToastStore } from '@/stores/toastStore'

const STYLE = {
  success: 'border-success bg-success-bg text-success',
  error: 'border-danger bg-danger-bg text-danger',
  info: 'border-info bg-info-bg text-info',
} as const
const ICON = { success: CheckCircle2, error: XCircle, info: Info } as const

export function Toaster() {
  const toasts = useToastStore((s) => s.toasts)
  const dismiss = useToastStore((s) => s.dismiss)
  return (
    <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2">
      {toasts.map((t) => {
        const Icon = ICON[t.kind]
        return (
          <div
            key={t.id}
            role={t.kind === 'error' ? 'alert' : 'status'}
            className={`pointer-events-auto flex items-start gap-2 rounded-lg border px-3 py-2 text-sm shadow-md ${STYLE[t.kind]}`}
          >
            <Icon size={18} className="mt-0.5 shrink-0" aria-hidden />
            <p className="flex-1">{t.message}</p>
            <button onClick={() => dismiss(t.id)} aria-label="Fechar notificação" className="rounded p-0.5 hover:bg-black/5">
              <X size={14} />
            </button>
          </div>
        )
      })}
    </div>
  )
}
