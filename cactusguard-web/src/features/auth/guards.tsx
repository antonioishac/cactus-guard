import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuthStore } from '@/stores/authStore'
import { hasAnyRole } from '@/lib/permissions'
import type { Role } from '@/api/types'
import { Splash } from './Splash'

/** Exige sessão. Sem sessão -> /login (guardando a origem). */
export function RequireAuth() {
  const status = useAuthStore((s) => s.status)
  const location = useLocation()
  if (status === 'unknown') return <Splash />
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <Outlet />
}

/** Exige uma das roles. Sem role -> /unauthorized. */
export function RequireRole({ roles }: { roles: readonly Role[] }) {
  const user = useAuthStore((s) => s.user)
  if (!user || !hasAnyRole(user.roles, roles)) return <Navigate to="/unauthorized" replace />
  return <Outlet />
}
