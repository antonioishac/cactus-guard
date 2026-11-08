import type { ReactNode } from 'react'
import { Link, useMatches } from 'react-router'
import { ChevronRight } from 'lucide-react'

export function Card({ title, actions, children, className = '' }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`rounded-xl border border-line bg-surface ${className}`}>
      {(title || actions) && (
        <header className="flex items-center justify-between gap-2 border-b border-line px-4 py-3">
          <h2 className="text-sm font-semibold">{title}</h2>
          {actions}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  )
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold">{title}</h1>
        {subtitle && <p className="text-sm text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

export interface RouteHandle {
  crumb?: string
}

/** Breadcrumbs a partir do `handle.crumb` de cada rota casada. */
export function Breadcrumbs() {
  const matches = useMatches()
  const crumbs = matches
    .map((m) => ({ path: m.pathname, label: (m.handle as RouteHandle | undefined)?.crumb }))
    .filter((c): c is { path: string; label: string } => !!c.label)
  if (crumbs.length <= 1) return null
  return (
    <nav aria-label="Trilha de navegação" className="mb-3">
      <ol className="flex flex-wrap items-center gap-1 text-sm text-muted">
        {crumbs.map((c, i) => (
          <li key={c.path} className="flex items-center gap-1">
            {i > 0 && <ChevronRight size={14} aria-hidden />}
            {i === crumbs.length - 1 ? (
              <span aria-current="page" className="font-medium text-ink">
                {c.label}
              </span>
            ) : (
              <Link to={c.path} className="underline-offset-2 hover:underline">
                {c.label}
              </Link>
            )}
          </li>
        ))}
      </ol>
    </nav>
  )
}

/** Par rótulo/valor para fichas. */
export function KeyValue({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="text-sm">{children ?? '—'}</dd>
    </div>
  )
}
