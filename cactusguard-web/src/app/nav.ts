import { Bell, ClipboardCheck, FileText, FolderOpen, LayoutDashboard, ScrollText, Search, Send, Settings2, Users, UserCheck } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { Role } from '@/api/types'

export interface NavItem {
  path: string
  label: string
  icon: LucideIcon
  /** ausente = qualquer usuário autenticado */
  roles?: readonly Role[]
  /** false = placeholder "Em construção" (etapa 2) */
  ready: boolean
}

export const AUDIT_ROLES: readonly Role[] = ['AUDITOR', 'ADMIN', 'SUPERVISOR']
export const ADMIN_ROLES: readonly Role[] = ['ADMIN']
export const SISCOAF_ROLES: readonly Role[] = ['SUPERVISOR', 'ADMIN', 'AUDITOR']
export const APPROVER_ROLES: readonly Role[] = ['SUPERVISOR']

/** Fonte única do menu E das rotas protegidas por role. */
export const NAV_ITEMS: NavItem[] = [
  { path: '/', label: 'Dashboard', icon: LayoutDashboard, ready: true },
  { path: '/alertas', label: 'Alertas', icon: Bell, ready: true },
  { path: '/casos', label: 'Casos', icon: FolderOpen, ready: true },
  { path: '/partes', label: 'Partes (KYC/PEP)', icon: UserCheck, ready: true },
  { path: '/screening', label: 'Screening', icon: Search, ready: true },
  { path: '/aprovacoes', label: 'Fila de aprovação', icon: ClipboardCheck, roles: APPROVER_ROLES, ready: true },
  { path: '/siscoaf', label: 'SISCOAF', icon: Send, roles: SISCOAF_ROLES, ready: true },
  { path: '/regras', label: 'Regras', icon: Settings2, ready: true },
  { path: '/relatorios', label: 'Relatórios', icon: FileText, ready: true },
  { path: '/auditoria', label: 'Auditoria', icon: ScrollText, roles: AUDIT_ROLES, ready: true },
  { path: '/usuarios', label: 'Usuários', icon: Users, roles: ADMIN_ROLES, ready: true },
]

