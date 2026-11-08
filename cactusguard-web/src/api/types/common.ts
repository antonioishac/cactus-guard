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

/** Códigos estáveis do contrato de erro global (spec 033). `BUSINESS_RULE` pode ser especializado (ex.: DOCUMENT_IMMUTABLE). */
export type ApiErrorCode =
  | 'VALIDATION_ERROR'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'METHOD_NOT_ALLOWED'
  | 'BUSINESS_RULE'
  | 'PAYLOAD_TOO_LARGE'
  | 'RATE_LIMITED'
  | 'INTERNAL_ERROR'

export interface FieldError {
  field: string
  message: string
}

/** Corpo de erro do GlobalExceptionHandler: `{timestamp,status,error,message,code,traceId,fieldErrors?}`. */
export interface ApiErrorBody {
  timestamp?: ISODateTime
  status?: number
  error?: string
  message?: string
  code?: ApiErrorCode | string
  traceId?: string
  fieldErrors?: FieldError[]
}
