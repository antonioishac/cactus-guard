import type { Role, UUID } from './common'

export interface LoginRequest {
  username: string
  password: string
}

/**
 * AuthResponse.UserInfo do backend: `id, username, name, roles`.
 * `tenantId`/`tenantName`: preparados para multi-tenancy (ainda não enviados); a UI exibe o nome se vier.
 * `permissions` não existe no backend (a UI deriva de `roles` em lib/permissions).
 */
export interface AuthUserDto {
  id: UUID
  username: string
  name: string
  roles: string[]
  tenantId?: string | null
  tenantName?: string | null
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  /** segundos */
  expiresIn: number
  user: AuthUserDto
}

export interface SessionUser {
  id: UUID
  username: string
  name: string
  roles: Role[]
  permissions: string[]
  tenantName?: string | null
}
