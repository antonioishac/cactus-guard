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

/** UserCreateRequest do backend (senha com 10 a 128 caracteres). */
export interface UserCreateRequest {
  username: string
  email: string
  password: string
  name: string
  roles: string[]
}

/** PROPOSTO (o backend só tem criar/listar/detalhe/desativar): edição, reativação e troca de roles. */
export interface UserUpdateRequest {
  name: string
  email: string
  roles: string[]
}
