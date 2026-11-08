import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from '@/mocks/server'
import { resetMockData } from '@/mocks/data'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { __resetRefreshForTests } from '@/api/client'

// jsdom não implementa matchMedia (usado no AppLayout)
if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({ matches: true, media: query, addEventListener: () => {}, removeEventListener: () => {}, addListener: () => {}, removeListener: () => {}, dispatchEvent: () => false, onchange: null }) as MediaQueryList
}

beforeAll(() => server.listen())
afterEach(() => {
  cleanup()
  server.resetHandlers()
  resetMockData()
  __resetRefreshForTests()
  sessionStorage.clear()
  useAuthStore.setState({ status: 'unknown', user: null, accessToken: null, refreshToken: null, refreshing: false, notice: null })
  useToastStore.setState({ toasts: [] })
})
afterAll(() => server.close())
