import { useAuthStore } from '@/stores/authStore'
import { can, hasAnyRole } from '@/lib/permissions'
import type { Action } from '@/lib/permissions'
import type { Role } from '@/api/types'

/** Acesso à sessão + helpers de role. Botões usam `can(...)`; o backend continua sendo a barreira. */
export function useAuth() {
  const user = useAuthStore((s) => s.user)
  const roles: Role[] = user?.roles ?? []
  return {
    user,
    roles,
    can: (action: Action) => can(roles, action),
    hasRole: (allowed: readonly Role[]) => hasAnyRole(roles, allowed),
    readOnly: !can(roles, 'case:decide'),
  }
}
