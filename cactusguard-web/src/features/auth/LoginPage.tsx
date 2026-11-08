import { useId } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { login } from '@/api/auth'
import { errorMessage, toApiError } from '@/api/errors'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/Button'
import { useAuthStore } from '@/stores/authStore'

const schema = z.object({
  username: z.string().trim().min(1, 'Informe o usuário ou e-mail.'),
  password: z.string().min(1, 'Informe a senha.'),
})
type Values = z.infer<typeof schema>

const NOTICE = {
  expired: 'Sua sessão expirou. Entre novamente.',
  idle: 'Sessão encerrada por inatividade. Entre novamente.',
  logout: '',
} as const

export function LoginPage() {
  const uid = useId()
  const navigate = useNavigate()
  const location = useLocation()
  const status = useAuthStore((s) => s.status)
  const notice = useAuthStore((s) => s.notice)
  const setSession = useAuthStore((s) => s.setSession)
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { username: '', password: '' } })

  if (status === 'authenticated') return <Navigate to={from} replace />

  const onSubmit = handleSubmit(async (v) => {
    try {
      setSession(await login(v))
      navigate(from, { replace: true })
    } catch (e) {
      const err = toApiError(e)
      setError('root', {
        message:
          err.status === 401
            ? 'Credenciais inválidas.'
            : err.status === 423 || err.status === 403
              ? 'Usuário bloqueado ou sem acesso. Contate o administrador.'
              : errorMessage(e),
      })
    }
  })

  return (
    <main className="flex min-h-screen items-center justify-center bg-canvas p-4">
      <div className="w-full max-w-sm rounded-2xl border border-line bg-surface p-6 shadow-sm">
        <div className="mb-6 flex items-center gap-3">
          <Logo className="h-10 w-10" />
          <h1 className="text-xl font-semibold">
            Cactus<span className="text-gold">Guard</span>
          </h1>
        </div>

        {notice && NOTICE[notice] && (
          <p role="status" className="mb-4 rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning">
            {NOTICE[notice]}
          </p>
        )}

        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <div>
            <label htmlFor={`${uid}-u`} className="mb-1 block text-sm font-medium">
              Usuário ou e-mail
            </label>
            <input
              id={`${uid}-u`}
              autoComplete="username"
              {...register('username')}
              aria-invalid={!!errors.username}
              className="w-full rounded-lg border border-line px-3 py-2 text-sm"
            />
            {errors.username && (
              <p role="alert" className="mt-1 text-xs text-danger">
                {errors.username.message}
              </p>
            )}
          </div>
          <div>
            <label htmlFor={`${uid}-p`} className="mb-1 block text-sm font-medium">
              Senha
            </label>
            <input
              id={`${uid}-p`}
              type="password"
              autoComplete="current-password"
              {...register('password')}
              aria-invalid={!!errors.password}
              className="w-full rounded-lg border border-line px-3 py-2 text-sm"
            />
            {errors.password && (
              <p role="alert" className="mt-1 text-xs text-danger">
                {errors.password.message}
              </p>
            )}
          </div>
          {errors.root && (
            <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">
              {errors.root.message}
            </p>
          )}
          <Button type="submit" className="w-full" loading={isSubmitting}>
            Entrar
          </Button>
        </form>
      </div>
    </main>
  )
}
