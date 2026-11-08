import { create } from 'zustand'
import { ROLES } from '@/api/types'
import type { AuthResponse, Role, SessionUser } from '@/api/types'

/**
 * Armazenamento de tokens (spec 022 pede "storage seguro" sem definir qual):
 * - accessToken: SOMENTE em memória (some no reload; é recuperado via refresh).
 * - refreshToken: memória + sessionStorage. Trade-off: sobrevive a reload da aba (UX) mas fica exposto a XSS;
 *   escapa de localStorage (persistente entre abas/sessões). Alternativa mais segura = cookie httpOnly
 *   emitido pelo backend (requer mudança no backend; ver STATUS.md).
 * Nunca persistimos dados de PII aqui.
 */
const RT_KEY = 'cg.rt'

function readStoredRefresh(): string | null {
  try {
    return sessionStorage.getItem(RT_KEY)
  } catch {
    return null
  }
}
function writeStoredRefresh(token: string | null) {
  try {
    if (token) sessionStorage.setItem(RT_KEY, token)
    else sessionStorage.removeItem(RT_KEY)
  } catch {
    /* storage indisponível: segue só em memória */
  }
}

export type AuthStatus = 'unknown' | 'authenticated' | 'anonymous'
export type ClearReason = 'logout' | 'expired' | 'idle'

interface AuthState {
  status: AuthStatus
  user: SessionUser | null
  accessToken: string | null
  refreshToken: string | null
  /** true enquanto um refresh está em andamento (indicador visual / spec 022) */
  refreshing: boolean
  /** mensagem para a tela de login (sessão expirada etc.) */
  notice: ClearReason | null
  setSession: (resp: AuthResponse) => void
  setRefreshing: (v: boolean) => void
  clear: (reason?: ClearReason) => void
  markAnonymous: () => void
}

/** Normaliza "ROLE_ANALYST" -> "ANALYST" e descarta papéis desconhecidos. */
export function normalizeRoles(raw: string[]): Role[] {
  return raw
    .map((r) => r.replace(/^ROLE_/, '').toUpperCase())
    .filter((r): r is Role => (ROLES as readonly string[]).includes(r))
}

export const useAuthStore = create<AuthState>((set) => ({
  status: 'unknown',
  user: null,
  accessToken: null,
  refreshToken: readStoredRefresh(),
  refreshing: false,
  notice: null,
  setSession: (resp) => {
    writeStoredRefresh(resp.refreshToken)
    set({
      status: 'authenticated',
      accessToken: resp.accessToken,
      refreshToken: resp.refreshToken,
      notice: null,
      user: {
        id: resp.user.id,
        username: resp.user.username ?? '',
        name: resp.user.name,
        roles: normalizeRoles(resp.user.roles),
        permissions: [],
        tenantName: resp.user.tenantName ?? null,
      },
    })
  },
  setRefreshing: (refreshing) => set({ refreshing }),
  clear: (reason = 'logout') => {
    writeStoredRefresh(null)
    set({
      status: 'anonymous',
      user: null,
      accessToken: null,
      refreshToken: null,
      refreshing: false,
      notice: reason === 'logout' ? null : reason,
    })
  },
  markAnonymous: () => set({ status: 'anonymous' }),
}))
