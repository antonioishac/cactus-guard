import { useCallback, useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router'
import { LogOut, Menu } from 'lucide-react'
import Sidebar from '@/components/Sidebar'
import { Breadcrumbs } from '@/components/ui/Layout'
import { useAuthStore } from '@/stores/authStore'
import { endSession } from '@/features/auth/session'
import { NotificationBell } from '@/features/notifications/NotificationBell'
import { useIdleLogout } from '@/features/auth/useIdleLogout'

const IDLE_MIN = Number(import.meta.env.VITE_IDLE_TIMEOUT_MIN ?? 15)
const DESKTOP = '(min-width: 1024px)'

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('')
}

export default function AppLayout() {
  const user = useAuthStore((s) => s.user)
  const refreshing = useAuthStore((s) => s.refreshing)
  const [isDesktop, setIsDesktop] = useState(() => window.matchMedia(DESKTOP).matches)
  const [open, setOpen] = useState(isDesktop)
  const location = useLocation()

  useEffect(() => {
    const mq = window.matchMedia(DESKTOP)
    const on = (e: MediaQueryListEvent) => {
      setIsDesktop(e.matches)
      setOpen(e.matches)
    }
    mq.addEventListener('change', on)
    return () => mq.removeEventListener('change', on)
  }, [])

  const onIdle = useCallback(() => void endSession('idle'), [])
  useIdleLogout(IDLE_MIN, onIdle)

  return (
    <div className="min-h-screen">
      <a href="#conteudo" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-[70] focus:rounded focus:bg-surface focus:px-3 focus:py-2">
        Pular para o conteúdo
      </a>
      <Sidebar open={open} onClose={() => setOpen(false)} closeOnNavigate={!isDesktop} />

      <div className={`transition-[margin] ${open ? 'lg:ml-64' : ''}`}>
        <header className="sticky top-0 z-30 flex items-center justify-between border-b border-line bg-surface px-4 py-3">
          <button
            onClick={() => setOpen((o) => !o)}
            aria-label={open ? 'Recolher menu' : 'Abrir menu'}
            aria-expanded={open}
            className="rounded-lg p-2 hover:bg-neutral-bg"
          >
            <Menu size={20} />
          </button>
          <div className="flex items-center gap-3">
            <NotificationBell />
            {refreshing && (
              <span role="status" className="text-xs text-muted">
                Renovando sessão…
              </span>
            )}
            <div className="flex items-center gap-2">
              <span aria-hidden className="flex h-8 w-8 items-center justify-center rounded-full bg-brand text-sm font-medium text-white">
                {user ? initials(user.name) : ''}
              </span>
              <div className="hidden text-left leading-tight sm:block">
                <p className="text-sm font-medium">{user?.name}</p>
                <p className="text-xs text-muted">{user?.roles.join(', ')}{user?.tenantName ? ` · ${user.tenantName}` : ''}</p>
              </div>
            </div>
            <button onClick={() => void endSession('logout')} className="flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-sm hover:bg-neutral-bg">
              <LogOut size={16} aria-hidden /> Sair
            </button>
          </div>
        </header>

        <main id="conteudo" className="p-4 sm:p-6" key={location.pathname.split('/')[1]}>
          <Breadcrumbs />
          <Outlet />
        </main>
      </div>
    </div>
  )
}
