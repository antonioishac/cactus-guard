import { Logo } from '@/components/Logo'

export function Splash() {
  return (
    <div role="status" aria-label="Carregando" className="flex min-h-screen items-center justify-center bg-canvas">
      <Logo className="h-12 w-12 animate-pulse" />
    </div>
  )
}
