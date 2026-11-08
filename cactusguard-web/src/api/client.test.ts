import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { useAuthStore } from '@/stores/authStore'
import { listAlerts } from './alerts'
import { ApiError } from './errors'
import { loginAs } from '@/test/utils'

const okPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }

function authed401Once() {
  let refreshCalls = 0
  server.use(
    http.get('*/api/v1/alerts', ({ request }) =>
      request.headers.get('authorization')?.includes('.new')
        ? HttpResponse.json(okPage)
        : HttpResponse.json({ message: 'expirado' }, { status: 401 }),
    ),
    http.post('*/api/v1/auth/refresh', async () => {
      refreshCalls += 1
      await new Promise((r) => setTimeout(r, 30)) // dá tempo de as 3 chamadas se enfileirarem
      return HttpResponse.json({ accessToken: 'acc.u-analyst.new', refreshToken: 'ref.u-analyst.new', expiresIn: 1800, user: { id: 'u-analyst', name: 'Ana', roles: ['ANALYST'] } })
    }),
  )
  return () => refreshCalls
}

describe('cliente HTTP: refresh automático em 401', () => {
  it('faz UM refresh para várias requisições simultâneas e repete todas', async () => {
    loginAs('u-analyst')
    const refreshCalls = authed401Once()

    const results = await Promise.all([listAlerts({}, {}), listAlerts({}, {}), listAlerts({}, {})])

    expect(results).toHaveLength(3)
    expect(refreshCalls()).toBe(1)
    expect(useAuthStore.getState().accessToken).toBe('acc.u-analyst.new')
    expect(useAuthStore.getState().refreshToken).toBe('ref.u-analyst.new')
  })

  it('em falha do refresh faz logout com aviso de sessão expirada', async () => {
    loginAs('u-analyst')
    server.use(
      http.get('*/api/v1/alerts', () => HttpResponse.json({ message: 'expirado' }, { status: 401 })),
      http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ message: 'inválido' }, { status: 401 })),
    )

    await expect(listAlerts({}, {})).rejects.toBeInstanceOf(ApiError)

    const s = useAuthStore.getState()
    expect(s.status).toBe('anonymous')
    expect(s.accessToken).toBeNull()
    expect(s.notice).toBe('expired')
    expect(sessionStorage.getItem('cg.rt')).toBeNull()
  })

  it('normaliza erro do contrato global (code, message, traceId, fieldErrors)', async () => {
    loginAs('u-analyst')
    server.use(
      http.get('*/api/v1/alerts', () => HttpResponse.json({ status: 422, error: 'Unprocessable Content', code: 'DOCUMENT_IMMUTABLE', message: 'Regra violada', traceId: 'abc123', fieldErrors: [{ field: 'document', message: 'imutável' }] }, { status: 422 })),
    )
    await expect(listAlerts({}, {})).rejects.toMatchObject({ status: 422, code: 'DOCUMENT_IMMUTABLE', message: 'Regra violada', traceId: 'abc123', fieldErrors: [{ field: 'document', message: 'imutável' }] })
  })

  it('anexa o Bearer token em memória', async () => {
    loginAs('u-analyst')
    let auth: string | null = null
    server.use(
      http.get('*/api/v1/alerts', ({ request }) => {
        auth = request.headers.get('authorization')
        return HttpResponse.json(okPage)
      }),
    )
    await listAlerts({}, {})
    expect(auth).toBe('Bearer acc.u-analyst.t')
    // access token nunca vai para storage
    expect(JSON.stringify({ ...sessionStorage })).not.toContain('acc.u-analyst')
  })
})
