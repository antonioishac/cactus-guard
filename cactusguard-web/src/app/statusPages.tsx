import { Link, useLocation } from 'react-router'
import { Construction } from 'lucide-react'
import { NAV_ITEMS } from './nav'
import { PageHeader } from '@/components/ui/Layout'
import { EmptyState } from '@/components/ui/Feedback'

export function UnderConstruction() {
  const { pathname } = useLocation()
  const item = NAV_ITEMS.find((i) => i.path !== '/' && pathname.startsWith(i.path))
  return (
    <>
      <PageHeader title={item?.label ?? 'Módulo'} />
      <div className="rounded-xl border border-line bg-surface">
        <EmptyState title="Em construção" hint="Este módulo será entregue na próxima etapa do frontend." action={<Construction aria-hidden className="text-muted" />} />
      </div>
    </>
  )
}

export function Unauthorized() {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <h1 className="text-xl font-semibold">Acesso negado</h1>
      <p className="mt-2 text-sm text-muted">Seu perfil não tem permissão para acessar esta área.</p>
      <Link to="/" className="mt-4 inline-block text-brand underline">Voltar ao início</Link>
    </div>
  )
}

export function NotFound() {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <h1 className="text-xl font-semibold">Página não encontrada</h1>
      <Link to="/" className="mt-4 inline-block text-brand underline">Voltar ao início</Link>
    </div>
  )
}
