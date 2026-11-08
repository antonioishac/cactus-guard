import { HttpResponse } from 'msw'
import type { Page } from '@/api/types'
import { MOCK_USERS } from '../data'
import type { MockUser } from '../data'

/** Helpers dos handlers da etapa 2B (cópia enxuta dos de handlers.ts, que não são exportados). */
export const API = '*/api/v1'

export function err(status: number, message: string) {
  return HttpResponse.json({ timestamp: new Date().toISOString(), status, error: String(status), message }, { status })
}

export function userFrom(request: Request): MockUser | null {
  const m = /^Bearer acc\.([^.]+)\./.exec(request.headers.get('authorization') ?? '')
  return MOCK_USERS.find((u) => u.id === m?.[1]) ?? null
}

/** Exige login e (opcionalmente) role; devolve o usuário ou a resposta de erro. */
export function guard(request: Request, roles?: string[]): MockUser | Response {
  const u = userFrom(request)
  if (!u) return err(401, 'Não autenticado')
  if (roles && !u.roles.some((r) => roles.includes(r))) return err(403, 'Acesso negado')
  return u
}
export const isResponse = (v: MockUser | Response): v is Response => v instanceof Response

export function paginate<T>(items: T[], url: URL): Page<T> {
  const page = Number(url.searchParams.get('page') ?? 0)
  const size = Number(url.searchParams.get('size') ?? 20)
  const [field, dir] = (url.searchParams.get('sort') ?? '').split(',')
  const sorted = [...items]
  if (field) {
    sorted.sort((a, b) => {
      const av = (a as Record<string, unknown>)[field]
      const bv = (b as Record<string, unknown>)[field]
      if (av == null && bv == null) return 0
      if (av == null) return 1
      if (bv == null) return -1
      return (av < bv ? -1 : av > bv ? 1 : 0) * (dir === 'desc' ? -1 : 1)
    })
  }
  return { content: sorted.slice(page * size, page * size + size), totalElements: items.length, totalPages: Math.ceil(items.length / size), number: page, size }
}

export const validJustification = (s: unknown) => typeof s === 'string' && s.replace(/\s/g, '').length >= 20
