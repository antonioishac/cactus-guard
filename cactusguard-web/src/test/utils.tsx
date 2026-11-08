import { render } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import { RouterProvider } from 'react-router/dom'
import { createQueryClient } from '@/app/queryClient'
import { createTestRouter } from '@/app/router'
import { Toaster } from '@/components/ui/Toast'
import { MOCK_USERS } from '@/mocks/data'
import { useAuthStore } from '@/stores/authStore'

/** Coloca um usuário do mock na sessão (sem passar pela tela de login). */
export function loginAs(userId: string) {
  const u = MOCK_USERS.find((x) => x.id === userId)!
  useAuthStore.getState().setSession({
    accessToken: `acc.${u.id}.t`,
    refreshToken: `ref.${u.id}.t`,
    expiresIn: 1800,
    user: { id: u.id, username: u.username, name: u.name, roles: u.roles },
  })
}

export function renderApp(path: string) {
  const router = createTestRouter([path])
  const utils = render(
    <QueryClientProvider client={createQueryClient()}>
      <RouterProvider router={router} />
      <Toaster />
    </QueryClientProvider>,
  )
  return { ...utils, router }
}

export function renderWithQuery(ui: ReactElement) {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      {ui}
      <Toaster />
    </QueryClientProvider>,
  )
}
