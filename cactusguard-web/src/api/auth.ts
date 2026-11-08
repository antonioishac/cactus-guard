import { api } from './client'
import type { AuthResponse, LoginRequest } from './types'

export async function login(body: LoginRequest): Promise<AuthResponse> {
  return (await api.post<AuthResponse>('/auth/login', body)).data
}

/** Backend exige o refreshToken no corpo (spec 033 diz "header Authorization"). */
export async function logout(refreshToken: string): Promise<void> {
  await api.post('/auth/logout', { refreshToken })
}
