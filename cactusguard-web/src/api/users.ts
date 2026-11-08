import { api } from './client'
import { pageQuery } from './params'
import type { Page, PageParams, PasswordResetRequest, PasswordResetResponse, UserAccount, UserCreateRequest, UserUpdateRequest, UUID } from './types'

export async function listUsers(paging: PageParams): Promise<Page<UserAccount>> {
  return (await api.get<Page<UserAccount>>('/users', { params: pageQuery(paging) })).data
}

export async function createUser(body: UserCreateRequest): Promise<UserAccount> {
  return (await api.post<UserAccount>('/users', body)).data
}

export async function updateUser(id: UUID, body: UserUpdateRequest): Promise<UserAccount> {
  return (await api.put<UserAccount>(`/users/${id}`, body)).data
}

export async function deactivateUser(id: UUID): Promise<UserAccount> {
  return (await api.post<UserAccount>(`/users/${id}/deactivate`)).data
}

export async function activateUser(id: UUID): Promise<UserAccount> {
  return (await api.post<UserAccount>(`/users/${id}/activate`)).data
}

/**
 * Sem `newPassword` o servidor gera uma senha forte e a devolve UMA vez em `temporaryPassword`.
 * Revoga todos os refresh tokens do usuário. O chamador nunca deve guardar a senha.
 */
export async function resetUserPassword(id: UUID, body: PasswordResetRequest = {}): Promise<PasswordResetResponse> {
  return (await api.post<PasswordResetResponse>(`/users/${id}/reset-password`, body)).data
}
