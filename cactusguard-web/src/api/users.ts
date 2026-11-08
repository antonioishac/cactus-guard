import { api } from './client'
import { pageQuery } from './params'
import type { Page, PageParams, UserAccount, UserCreateRequest, UserUpdateRequest, UUID } from './types'

export async function listUsers(paging: PageParams): Promise<Page<UserAccount>> {
  return (await api.get<Page<UserAccount>>('/users', { params: pageQuery(paging) })).data
}

export async function createUser(body: UserCreateRequest): Promise<UserAccount> {
  return (await api.post<UserAccount>('/users', body)).data
}

export async function deactivateUser(id: UUID): Promise<UserAccount> {
  return (await api.post<UserAccount>(`/users/${id}/deactivate`)).data
}

/** DIVERGÊNCIA (PROPOSTO): edição de dados e roles. Backend não tem `PUT /users/{id}`. */
export async function updateUser(id: UUID, body: UserUpdateRequest): Promise<UserAccount> {
  return (await api.put<UserAccount>(`/users/${id}`, body)).data
}

/** DIVERGÊNCIA (PROPOSTO): reativação. Backend não tem `POST /users/{id}/activate`. */
export async function activateUser(id: UUID): Promise<UserAccount> {
  return (await api.post<UserAccount>(`/users/${id}/activate`)).data
}
