import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/authStore'
import { toApiError } from './errors'
import type { AuthResponse } from './types'

export const API_URL: string = import.meta.env.VITE_API_URL ?? '/api/v1'

export const api = axios.create({ baseURL: API_URL, timeout: 30_000 })
/** Instância sem interceptors: usada só para o refresh (evita laço de 401). */
const bare = axios.create({ baseURL: API_URL, timeout: 30_000 })

const AUTH_PATHS = ['/auth/login', '/auth/refresh']
const isAuthPath = (url?: string) => !!url && AUTH_PATHS.some((p) => url.includes(p))

/** Fila única: todas as requisições que tomam 401 aguardam a MESMA promise de refresh. */
let refreshPromise: Promise<string> | null = null

export function refreshSession(): Promise<string> {
  if (refreshPromise) return refreshPromise
  const { refreshToken, setSession, clear, setRefreshing } = useAuthStore.getState()
  if (!refreshToken) {
    clear('expired')
    return Promise.reject(new Error('sem refresh token'))
  }
  setRefreshing(true)
  refreshPromise = bare
    .post<AuthResponse>('/auth/refresh', { refreshToken })
    .then((res) => {
      setSession(res.data)
      return res.data.accessToken
    })
    .catch((err: unknown) => {
      clear('expired')
      throw err
    })
    .finally(() => {
      refreshPromise = null
      useAuthStore.getState().setRefreshing(false)
    })
  return refreshPromise
}

api.interceptors.request.use(async (config) => {
  if (isAuthPath(config.url)) return config
  // enquanto há refresh em curso, a requisição espera (spec 022: bloqueio durante refresh)
  if (refreshPromise) {
    try {
      await refreshPromise
    } catch {
      /* segue; resposta 401 será tratada abaixo */
    }
  }
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  return config
})

type RetriableConfig = InternalAxiosRequestConfig & { _retry?: boolean }

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined
    if (error.response?.status === 401 && original && !original._retry && !isAuthPath(original.url)) {
      original._retry = true
      try {
        const token = await refreshSession()
        original.headers.set('Authorization', `Bearer ${token}`)
        return await api.request(original)
      } catch (e) {
        throw toApiError(e)
      }
    }
    throw toApiError(error)
  },
)

/** Somente para testes. */
export function __resetRefreshForTests() {
  refreshPromise = null
}
