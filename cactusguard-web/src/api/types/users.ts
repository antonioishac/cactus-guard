import type { ISODateTime, UUID } from './common'

/** UserResponse do backend. `roles` são os nomes sem prefixo ROLE_. */
export interface UserAccount {
  id: UUID
  username: string
  email: string
  name: string
  active: boolean
  roles: string[]
  createdAt: ISODateTime
}

/** UserCreateRequest (senha com 10 a 128 caracteres). */
export interface UserCreateRequest {
  username: string
  email: string
  password: string
  name: string
  roles: string[]
}

/** PUT /users/{id}: username é imutável; e-mail duplicado => 422 EMAIL_IN_USE. */
export interface UserUpdateRequest {
  name: string
  email: string
  roles: string[]
}

/** POST /users/{id}/reset-password: `newPassword` opcional (10–128). */
export interface PasswordResetRequest {
  newPassword?: string
}

/**
 * `temporaryPassword` só vem quando o servidor gera a senha, UMA única vez (no-store). Nunca persistir:
 * exibir com aviso e cópia, e descartar ao fechar.
 */
export interface PasswordResetResponse {
  userId: UUID
  temporaryPassword: string | null
  refreshTokensRevoked: boolean
}
