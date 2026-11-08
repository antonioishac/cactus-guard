import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { errorMessage } from '@/api/errors'
import { ROLES } from '@/api/types'
import type { UserAccount } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { useCreateUser, useUpdateUser } from '../hooks'
import { ROLE_INFO, ROLE_ORDER } from '../roles'

const base = {
  name: z.string().trim().min(3, 'Informe o nome completo.'),
  email: z.string().trim().regex(/^[^\s@]+@[^\s@]+\.[^\s@]+$/, 'E-mail inválido.'),
  roles: z.array(z.enum(ROLES)).min(1, 'Selecione ao menos um perfil.'),
}
const createSchema = z.object({
  ...base,
  username: z.string().trim().min(3, 'Mínimo de 3 caracteres.').regex(/^[a-z0-9._-]+$/i, 'Use letras, números, ponto, hífen ou sublinhado.'),
  password: z.string().min(10, 'A senha deve ter entre 10 e 128 caracteres.').max(128, 'A senha deve ter entre 10 e 128 caracteres.'),
})
const editSchema = z.object({ ...base, username: z.string().optional(), password: z.string().optional() })
type Values = z.infer<typeof createSchema>

const CONTROL = 'w-full rounded-lg border border-line px-3 py-2 text-sm disabled:bg-neutral-bg'

export function UserFormDialog({ user, onClose }: { user?: UserAccount; onClose: () => void }) {
  const editing = !!user
  const create = useCreateUser()
  const update = useUpdateUser()
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(editing ? editSchema : createSchema) as never,
    defaultValues: { name: user?.name ?? '', email: user?.email ?? '', username: user?.username ?? '', password: '', roles: (user?.roles ?? []) as Values['roles'] },
  })

  const submit = handleSubmit(async (v) => {
    try {
      if (user) await update.mutateAsync({ id: user.id, body: { name: v.name.trim(), email: v.email.trim(), roles: v.roles } })
      else await create.mutateAsync({ username: v.username.trim(), email: v.email.trim(), name: v.name.trim(), password: v.password, roles: v.roles })
      onClose()
    } catch (e) {
      setError('root', { message: errorMessage(e) })
    }
  })
  const err = (m?: string) => m && <p role="alert" className="mt-1 text-xs text-danger">{m}</p>

  return (
    <Modal open title={editing ? 'Editar usuário' : 'Novo usuário'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-3">
        <div>
          <label htmlFor="u-name" className="mb-1 block text-sm font-medium">Nome</label>
          <input id="u-name" {...register('name')} className={CONTROL} />
          {err(errors.name?.message)}
        </div>
        <div>
          <label htmlFor="u-username" className="mb-1 block text-sm font-medium">Usuário (login)</label>
          <input id="u-username" autoComplete="off" disabled={editing} {...register('username')} className={CONTROL} />
          {err(errors.username?.message)}
        </div>
        <div>
          <label htmlFor="u-email" className="mb-1 block text-sm font-medium">E-mail</label>
          <input id="u-email" type="email" autoComplete="off" {...register('email')} className={CONTROL} />
          {err(errors.email?.message)}
        </div>
        {!editing && (
          <div>
            <label htmlFor="u-pass" className="mb-1 block text-sm font-medium">Senha inicial (10 a 128 caracteres)</label>
            <input id="u-pass" type="password" autoComplete="new-password" {...register('password')} className={CONTROL} />
            {err(errors.password?.message)}
          </div>
        )}
        <fieldset>
          <legend className="mb-1 text-sm font-medium">Perfis</legend>
          <div className="space-y-1.5">
            {ROLE_ORDER.map((r) => (
              <label key={r} className="flex items-start gap-2 text-sm">
                <input type="checkbox" value={r} {...register('roles')} className="mt-0.5 h-4 w-4" />
                <span><strong>{ROLE_INFO[r].label}</strong> <span className="text-muted">{ROLE_INFO[r].description}</span></span>
              </label>
            ))}
          </div>
          {err(errors.roles?.message)}
        </fieldset>
        {errors.root && <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">{errors.root.message}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>Cancelar</Button>
          <Button type="submit" loading={isSubmitting}>{editing ? 'Salvar' : 'Criar usuário'}</Button>
        </div>
      </form>
    </Modal>
  )
}
