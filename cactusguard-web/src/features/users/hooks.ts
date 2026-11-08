import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { activateUser, createUser, deactivateUser, listUsers, resetUserPassword, updateUser } from '@/api/users'
import type { PageParams, UserCreateRequest, UserUpdateRequest, UUID } from '@/api/types'

export const useUsers = (paging: PageParams) =>
  useQuery({ queryKey: ['users', 'list', paging], queryFn: () => listUsers(paging), placeholderData: keepPreviousData })

function useUserAction<V, R>(fn: (v: V) => Promise<R>, successMessage: string) {
  const qc = useQueryClient()
  return useMutation({ mutationFn: fn, meta: { successMessage }, onSuccess: () => void qc.invalidateQueries({ queryKey: ['users'] }) })
}

export const useCreateUser = () => useUserAction((b: UserCreateRequest) => createUser(b), 'Usuário criado.')
export const useUpdateUser = () => useUserAction((v: { id: UUID; body: UserUpdateRequest }) => updateUser(v.id, v.body), 'Usuário atualizado.')
export const useDeactivateUser = () => useUserAction((id: UUID) => deactivateUser(id), 'Usuário desativado.')
export const useActivateUser = () => useUserAction((id: UUID) => activateUser(id), 'Usuário reativado.')

/**
 * Reset de senha: `gcTime: 0` evita que a senha temporária (devolvida UMA vez) fique no cache de mutações.
 * Erros são mostrados inline pelo diálogo (silent).
 */
export const useResetPassword = () =>
  useMutation({
    mutationFn: (v: { id: UUID; newPassword?: string }) => resetUserPassword(v.id, v.newPassword ? { newPassword: v.newPassword } : {}),
    meta: { silent: true },
    gcTime: 0,
  })
