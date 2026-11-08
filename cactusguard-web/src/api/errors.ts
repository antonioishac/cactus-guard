import axios from 'axios'
import type { ApiErrorBody } from './types'

/** Erro padronizado da API (código, mensagem, traceId). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly traceId?: string

  constructor(init: { status: number; code: string; message: string; traceId?: string }) {
    super(init.message)
    this.name = 'ApiError'
    this.status = init.status
    this.code = init.code
    this.traceId = init.traceId
  }
}

const FALLBACK: Record<number, string> = {
  400: 'Requisição inválida.',
  401: 'Sessão expirada ou credenciais inválidas.',
  403: 'Acesso negado para esta ação.',
  404: 'Recurso não encontrado.',
  409: 'Conflito com o estado atual do registro.',
  422: 'Não foi possível concluir: regra de negócio não atendida.',
  429: 'Muitas requisições. Aguarde alguns instantes.',
  500: 'Erro interno. Tente novamente em instantes.',
}

/** Converte qualquer erro (Axios ou não) em ApiError. Não inclui corpo da requisição (pode ter PII). */
export function toApiError(err: unknown): ApiError {
  if (err instanceof ApiError) return err
  if (axios.isAxiosError<ApiErrorBody>(err)) {
    if (!err.response) {
      return new ApiError({ status: 0, code: 'NETWORK', message: 'Sem conexão com o servidor.' })
    }
    const { status, data, headers } = err.response
    const body = typeof data === 'object' && data !== null ? data : undefined
    const headerTrace = (headers['x-trace-id'] ?? headers['x-request-id']) as string | undefined
    return new ApiError({
      status,
      code: body?.code ?? body?.error ?? `HTTP_${status}`,
      message: body?.message || FALLBACK[status] || `Erro ${status}.`,
      traceId: body?.traceId ?? headerTrace,
    })
  }
  return new ApiError({ status: 0, code: 'UNKNOWN', message: 'Erro inesperado.' })
}

export function errorMessage(err: unknown): string {
  const e = toApiError(err)
  return e.traceId ? `${e.message} (ref. ${e.traceId})` : e.message
}
