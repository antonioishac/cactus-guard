import { logout as logoutRequest } from '@/api/auth'
import { refreshSession } from '@/api/client'
import { useAuthStore } from '@/stores/authStore'
import type { ClearReason } from '@/stores/authStore'

/** Chamado uma vez na inicialização: tenta recuperar a sessão pelo refresh token (sessionStorage). */
export async function bootstrapSession(): Promise<void> {
  const { refreshToken, markAnonymous } = useAuthStore.getState()
  if (!refreshToken) {
    markAnonymous()
    return
  }
  try {
    await refreshSession()
  } catch {
    /* refreshSession já limpou a sessão */
  }
}

/** Revoga o refresh token no backend (melhor esforço) e limpa o estado local. */
export async function endSession(reason: ClearReason = 'logout'): Promise<void> {
  const { refreshToken, clear } = useAuthStore.getState()
  try {
    if (refreshToken && reason === 'logout') await logoutRequest(refreshToken)
  } catch {
    /* mesmo se falhar, encerra localmente */
  } finally {
    clear(reason)
  }
}
