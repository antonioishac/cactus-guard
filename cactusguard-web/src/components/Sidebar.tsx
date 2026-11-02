import { NavLink } from 'react-router-dom'
import { LayoutDashboard, Bell, FolderOpen, Search, UserCheck, FileText, Activity, Settings, X } from 'lucide-react'

const menuItems = [
  { path: '/', icon: LayoutDashboard, label: 'Dashboard' },
  { path: '/alertas', icon: Bell, label: 'Alertas' },
  { path: '/casos', icon: FolderOpen, label: 'Casos' },
  { path: '/screening', icon: Search, label: 'Screening' },
  { path: '/kyc', icon: UserCheck, label: 'KYC' },
  { path: '/relatorios', icon: FileText, label: 'Relatórios' },
  { path: '/monitoramento', icon: Activity, label: 'Monitoramento' },
  { path: '/configuracoes', icon: Settings, label: 'Configurações' },
]

interface SidebarProps {
  open: boolean
  onClose: () => void
}

export default function Sidebar({ open, onClose }: SidebarProps) {
  return (
    <>
      {/* Overlay mobile */}
      {open && <div className="fixed inset-0 bg-black/50 z-40 lg:hidden" onClick={onClose} />}

      {/* Sidebar */}
      <aside className={`fixed top-0 left-0 h-full w-64 bg-sidebar text-white z-50 transform transition-transform ${open ? 'translate-x-0' : '-translate-x-full'} lg:translate-x-0`}>
        {/* Logo */}
        <div className="p-4 flex items-center gap-3 border-b border-white/10">
          <div className="w-8 h-8 bg-accent rounded-lg flex items-center justify-center">
            <span className="text-white font-bold text-sm">CG</span>
          </div>
          <span className="font-semibold text-lg">CactusGuard</span>
          <button onClick={onClose} className="ml-auto lg:hidden p-1 hover:bg-white/10 rounded">
            <X size={18} />
          </button>
        </div>

        {/* Menu */}
        <nav className="p-3 space-y-1">
          {menuItems.map(item => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors ${
                  isActive ? 'bg-accent text-white' : 'text-white/70 hover:bg-white/10 hover:text-white'
                }`
              }
            >
              <item.icon size={18} />
              <span className="text-sm">{item.label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>
    </>
  )
}
