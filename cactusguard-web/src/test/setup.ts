import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from '@/mocks/server'
import { resetMockData } from '@/mocks/data'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { __resetRefreshForTests } from '@/api/client'
import { preloadLazyRoutes } from './preloadRoutes'

// `findBy*`/`waitFor` esperam por render + MSW; 1 s (padrão) é curto quando os arquivos de teste rodam em paralelo.
configure({ asyncUtilTimeout: 5000 })

// jsdom não implementa matchMedia (usado no AppLayout)
if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({ matches: true, media: query, addEventListener: () => {}, removeEventListener: () => {}, addListener: () => {}, removeListener: () => {}, dispatchEvent: () => false, onchange: null }) as MediaQueryList
}

// requisição sem handler = falha do teste (o mock não pode "mentir" respondendo algo que o backend não tem)
const unhandled: string[] = []
server.events.on('request:unhandled', ({ request }) => {
  unhandled.push(`${request.method} ${new URL(request.url).pathname}`)
})

beforeAll(async () => {
  server.listen()
  await preloadLazyRoutes()
}, 60_000)

// Isolamento: TODO o estado mutável dos mocks, handlers, storage, stores e toasts voltam ao inicial a cada teste.
// O QueryClient é novo a cada render (`renderApp`/`renderWithQuery` chamam `createQueryClient()`).
afterEach(() => {
  cleanup()
  server.resetHandlers()
  resetMockData()
  __resetRefreshForTests()
  sessionStorage.clear()
  localStorage.clear()
  useAuthStore.setState({ status: 'unknown', user: null, accessToken: null, refreshToken: null, refreshing: false, notice: null })
  useToastStore.setState({ toasts: [] })
  const missing = unhandled.splice(0)
  if (missing.length > 0) throw new Error(`Requisições sem handler MSW: ${[...new Set(missing)].join(', ')}`)
})
afterAll(() => server.close())
