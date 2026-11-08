import { HttpResponse } from 'msw'
import type { ApiErrorCode, FieldError, Page } from '@/api/types'
import { MOCK_USERS } from '../data'
import type { MockUser } from '../data'

/** Helpers dos handlers MSW. Espelham o contrato real: erro `{timestamp,status,error,message,code,traceId,fieldErrors?}` + `X-Trace-Id`. */
export const API = '*/api/v1'

const DEFAULT_CODE: Record<number, ApiErrorCode> = {
  400: 'VALIDATION_ERROR',
  401: 'UNAUTHORIZED',
  403: 'FORBIDDEN',
  404: 'NOT_FOUND',
  405: 'METHOD_NOT_ALLOWED',
  413: 'PAYLOAD_TOO_LARGE',
  422: 'BUSINESS_RULE',
  429: 'RATE_LIMITED',
  500: 'INTERNAL_ERROR',
}
const REASON: Record<number, string> = {
  400: 'Bad Request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not Found',
  413: 'Content Too Large',
  422: 'Unprocessable Content',
  429: 'Too Many Requests',
  500: 'Internal Server Error',
}

let traceSeq = 0

export function err(status: number, message: string, code?: string, fieldErrors?: FieldError[]) {
  const traceId = `mock-trace-${String(++traceSeq).padStart(6, '0')}`
  return HttpResponse.json(
    {
      timestamp: new Date().toISOString(),
      status,
      error: REASON[status] ?? String(status),
      message,
      code: code ?? DEFAULT_CODE[status] ?? 'INTERNAL_ERROR',
      traceId,
      ...(fieldErrors ? { fieldErrors } : {}),
    },
    { status, headers: { 'X-Trace-Id': traceId } },
  )
}

/** 400 de validação de corpo, com `fieldErrors`. */
export const invalid = (message: string, fieldErrors: FieldError[]) => err(400, message, 'VALIDATION_ERROR', fieldErrors)

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

export const READ = ['ANALYST', 'SUPERVISOR', 'ADMIN', 'AUDITOR']
export const ANALYSTS = ['ANALYST', 'SUPERVISOR']
export const SUP = ['SUPERVISOR']
export const AUDIT = ['AUDITOR', 'ADMIN', 'SUPERVISOR']

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

/** Valores múltiplos: repetidos (`a=1&a=2`) ou CSV (`a=1,2`). Vazio = sem filtro. */
export function multi(url: URL, key: string): string[] {
  return url.searchParams
    .getAll(key)
    .flatMap((v) => v.split(','))
    .map((v) => v.trim())
    .filter(Boolean)
}

/** `AAAA-MM-DD` (inclusivo até o fim do dia, `to`) ou ISO-8601 -> epoch ms; inválido => null. */
export function parseDay(value: string | null, endOfDay: boolean): number | null | undefined {
  if (!value) return undefined
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return new Date(`${value}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}-03:00`).getTime()
  const t = Date.parse(value)
  return Number.isNaN(t) ? null : t
}

export const nonBlank = (s: unknown) => (typeof s === 'string' ? s.replace(/\s/g, '').length : 0)
export const validJustification = (s: unknown) => nonBlank(s) >= 20

export const JUSTIFICATION_MSG = 'Justificativa obrigatória (mínimo de 20 caracteres não brancos)'

/** Lê o corpo JSON sem lançar (corpo ausente/ inválido => {}). */
export async function jsonBody<T extends object>(request: Request): Promise<Partial<T>> {
  return ((await request.json().catch(() => ({}))) ?? {}) as Partial<T>
}

export interface MultipartFile {
  name: string
  size: number
}

/**
 * Lê um corpo multipart sem `request.formData()` (que falha com o FormData do jsdom em Node).
 * Devolve o arquivo do campo `file` (nome e tamanho) e os demais campos de texto.
 */
export async function readMultipart(request: Request): Promise<{ file: MultipartFile | null; fields: Record<string, string> }> {
  const raw = new TextDecoder('latin1').decode(await request.arrayBuffer())
  const boundary = /boundary=([^;]+)/.exec(request.headers.get('content-type') ?? '')?.[1]?.replace(/^"|"$/g, '')
  const out: { file: MultipartFile | null; fields: Record<string, string> } = { file: null, fields: {} }
  if (!boundary) return out
  for (const part of raw.split(`--${boundary}`)) {
    const [head, ...rest] = part.split('\r\n\r\n')
    if (!head || rest.length === 0) continue
    const body = rest.join('\r\n\r\n').replace(/\r\n$/, '')
    const name = /name="([^"]*)"/.exec(head)?.[1]
    const filename = /filename="([^"]*)"/.exec(head)?.[1]
    if (name === 'file' && filename !== undefined) out.file = { name: filename, size: body.length }
    else if (name) out.fields[name] = body
  }
  return out
}
