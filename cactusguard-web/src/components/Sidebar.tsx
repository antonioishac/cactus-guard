import { NavLink } from 'react-router'
import { X } from 'lucide-react'
import { NAV_ITEMS } from '@/app/nav'
import { hasAnyRole } from '@/lib/permissions'
import { useAuthStore } from '@/stores/authStore'
import { Logo } from './Logo'

interface SidebarProps {
  open: boolean
  onClose: () => void
  /** fecha o menu ao navegar (mobile/tablet) */
  closeOnNavigate: boolean
}

export default function Sidebar({ open, onClose, closeOnNavigate }: SidebarProps) {
  const roles = useAuthStore((s) => s.user?.roles ?? [])
  const items = NAV_ITEMS.filter((i) => !i.roles || hasAnyRole(roles, i.roles))

  return (
    <>
      {open && closeOnNavigate && <div className="fixed inset-0 z-40 bg-black/50 lg:hidden" onClick={onClose} aria-hidden />}

      <aside
        aria-label="Menu principal"
        className={`fixed left-0 top-0 z-50 flex h-full w-64 transform flex-col bg-sidebar text-white transition-transform ${open ? 'translate-x-0' : '-translate-x-full'}`}
      >
        <div className="flex items-center gap-3 border-b border-white/10 p-4">
          <Logo />
          <span className="text-lg font-semibold">
            Cactus<span className="text-gold">Guard</span>
          </span>
          <button onClick={onClose} aria-label="Fechar menu" className="ml-auto rounded p-1 hover:bg-white/10 lg:hidden">
            <X size={18} />
          </button>
        </div>

        <nav aria-label="Módulos" className="flex-1 space-y-1 overflow-y-auto p-3">
          {items.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.path === '/'}
              onClick={closeOnNavigate ? onClose : undefined}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2.5 transition-colors ${
                  isActive ? 'bg-accent font-medium text-sidebar' : 'text-white/80 hover:bg-white/10 hover:text-white'
                }`
              }
            >
              <item.icon size={18} aria-hidden />
              <span className="text-sm">{item.label}</span>
              {!item.ready && <span className="ml-auto text-[10px] uppercase opacity-60">em breve</span>}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-white/10 p-3 text-xs text-white/60">CactusGuard Web</div>
      </aside>
    </>
  )
}
