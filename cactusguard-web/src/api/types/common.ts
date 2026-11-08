/** Tipos compartilhados. Espelham o `Page` do Spring Data e os papéis do backend. */
export type UUID = string
export type ISODateTime = string
export type ISODate = string

export const ROLES = ['ADMIN', 'SUPERVISOR', 'ANALYST', 'AUDITOR'] as const
export type Role = (typeof ROLES)[number]

/** Page<T> do Spring (campos usados pelo front). Página é 0-based. */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface PageParams {
  page?: number
  size?: number
  /** Formato Spring: `campo,asc|desc` */
  sort?: string
}

/** Corpo de erro do GlobalExceptionHandler; `code` e `traceId` ainda não existem no backend. */
export interface ApiErrorBody {
  timestamp?: ISODateTime
  status?: number
  error?: string
  message?: string
  code?: string
  traceId?: string
}
