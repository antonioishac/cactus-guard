import type { Role, UUID } from './common'

export interface LoginRequest {
  username: string
  password: string
}

/** AuthResponse.UserInfo do backend: traz roles; `permissions` (spec 033) ainda não é enviado. */
export interface AuthUserDto {
  id: UUID
  username?: string
  name: string
  roles: string[]
  permissions?: string[]
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
}
